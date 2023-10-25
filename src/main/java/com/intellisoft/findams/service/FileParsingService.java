package com.intellisoft.findams.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellisoft.findams.constants.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Slf4j
@Service
public class FileParsingService {
    private static final List<String> processedFilePaths = new ArrayList<>();
    private final HttpClientService httpClientService;
    private Map<String, String> attributeToColumnMapping = initializeAttributeToColumnMapping();

    @Autowired
    public FileParsingService(HttpClientService httpClientService) {
        this.httpClientService = httpClientService;
    }

    private static Map<String, String> initializeAttributeToColumnMapping() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("Organism", "ORGANISM");
        mapping.put("Organism Type", "ORG_TYPE");
        mapping.put("Patient unique ID", "PATIENT_ID");
        mapping.put("First Name", "FIRST_NAME");
        mapping.put("Last Name", "LAST_NAME");
        mapping.put("Middle Name", "X_MIDDLE_N");
        mapping.put("sex", "SEX");
        mapping.put("Age with Age Unit(Years,Months,Days)", "AGE");
        mapping.put("County", "X_COUNTY");
        mapping.put("Sub-county", "X_S_COUNTY");
        mapping.put("Diagnosis", "X_DIAGN");
        mapping.put("Ward", "WARD");
        mapping.put("Department", "DEPARTMENT");
        mapping.put("Ward Type", "WARD_TYPE");
        mapping.put("Date of admission", "DATE_ADMIS");
        mapping.put("Specimen/sample Number", "SPEC_NUM");
        mapping.put("Isolate Number/Test", "ISOL_NUM");
        mapping.put("Specimen collection date", "SPEC_DATE");
        mapping.put("Specimen Type", "SPEC_TYPE");
        mapping.put("Specimen Source", "X_SOURCE");
        mapping.put("Method", "X_METHOD");
        return mapping;
    }

    public Disposable parseFile(String filePath, String fileContent) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Sheet1");

        String[] lines = fileContent.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Row row = sheet.createRow(i);

            String[] values = line.split("\\|");

            for (int j = 0; j < values.length; j++) {
                Cell cell = row.createCell(j);
                cell.setCellValue(values[j]);
            }
        }

        // Process the Excel file to identify duplicates and assign new unique codes
        processExcelFile(sheet);

        // Fetch tracked entity attributes from DHIS2 API:
        Disposable subscription = httpClientService.fetchTrackedEntityAttributes()
                .subscribe(
                        attributesResponse -> {
                            // Create a list to hold the main payload
                            List<Map<String, Object>> bulkPayload = new ArrayList<>();

                            // Initialize attribute to columns mapping
                            Map<String, String> attributeToColumnMapping = initializeAttributeToColumnMapping();

                            Map<String, String> attributeIdMapping = createAttributeIdMapping(attributesResponse);

                            // Iterate over the Excel data
                            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                                Row excelRow = sheet.getRow(i);

                                // Create a payload for the tracked entity instance
                                Map<String, Object> trackedEntityInstance = new HashMap<>();
                                trackedEntityInstance.put("trackedEntityType", "sf54V4IRUD8"); // tracked entity ID
                                trackedEntityInstance.put("orgUnit", "YgHyNJOU8hs"); // org unit ID

                                // Create a list to hold attributes for this instance
                                List<Map<String, Object>> attributesList = new ArrayList<>();

                                // Iterate over the tracked entity attributes and Excel columns
                                for (Map.Entry<String, String> entry : attributeToColumnMapping.entrySet()) {
                                    String attributeDisplayName = entry.getKey();
                                    String columnMapping = entry.getValue();
                                    int columnIndex = getColumnIndex(sheet.getRow(0), columnMapping);

                                    if (columnIndex >= 0) {
                                        String cellValue = getCellValue(excelRow, columnIndex);
                                        String attributeId = attributeIdMapping.get(attributeDisplayName);
                                        Map<String, Object> attributeEntry = new HashMap<>();
                                        attributeEntry.put("attribute", attributeId);
                                        attributeEntry.put("id", cellValue);

                                        attributesList.add(attributeEntry);
                                    }
                                }

                                trackedEntityInstance.put("attributes", attributesList);

                                // Add the tracked entity instance to the bulk payload
                                bulkPayload.add(trackedEntityInstance);
                            }

                            // Create the final bulk payload map
                            Map<String, Object> finalBulkPayload = new HashMap<>();
                            finalBulkPayload.put("trackedEntityInstances", bulkPayload);

                            // Convert the finalBulkPayload to a JSON string
                            String jsonString = null;
                            try {
                                jsonString = new ObjectMapper().writeValueAsString(finalBulkPayload);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }

                            System.out.println("finalBulkPayload: " + jsonString);

                            httpClientService.postTrackedEntityInstances(finalBulkPayload)
                                    .doOnError(error -> {
                                        System.err.println("Error occurred: " + error);
                                    })
                                    .subscribe(response -> {

                                        System.out.println("Response: " + response);
//                                        processedFilePaths.add(filePath);
//                                        // Implement batching logic
//                                        batchProcessedFiles(processedFilePaths);
                                    });
                        },
                        error -> {
                            System.err.println("Error occurred while fetching attributes: " + error.getMessage());
                        }
                );

        return subscription;
    }


    private Map<String, String> createAttributeIdMapping(JsonNode trackedEntityAttributesResponse) {
        Map<String, String> attributeIdMapping = new HashMap<>();
        for (JsonNode attribute : trackedEntityAttributesResponse.get("trackedEntityAttributes")) {
            String displayName = attribute.get("displayName").asText();
            String id = attribute.get("id").asText();
            attributeIdMapping.put(displayName, id);
        }

        return attributeIdMapping;
    }


    private int getColumnIndex(Row headerRow, String columnName) {
        for (int j = 0; j < headerRow.getLastCellNum(); j++) {
            Cell cell = headerRow.getCell(j);
            if (cell.getStringCellValue().equalsIgnoreCase(columnName)) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    private String getCellValue(Row row, int columnIndex) {
        if (row != null) {
            Cell cell = row.getCell(columnIndex);
            if (cell != null) {
                return cell.getStringCellValue();
            }
        }
        return null;
    }

    private void processExcelFile(Sheet sheet) {
        Set<String> uniqueValues = new HashSet<>();

        Row headerRow = sheet.getRow(0);

        Map<String, String> columnToAttributeMapping = new HashMap<>();
        for (Map.Entry<String, String> entry : attributeToColumnMapping.entrySet()) {
            columnToAttributeMapping.put(entry.getValue(), entry.getKey());
        }

        int columnIndex = -1;

        // looking for the SPEC_NUM column
        for (Cell cell : headerRow) {
            if (cell.getStringCellValue().equals("SPEC_NUM")) {
                columnIndex = cell.getColumnIndex();
                break;
            }
        }

        // Get the current year
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);

        // Process the Excel sheet using the mapping
        if (columnIndex >= 0) {
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (row != null) {
                    Cell specNumCell = row.getCell(columnIndex);

                    // Check if the cell exists and is not null
                    if (specNumCell != null) {
                        String specNum = specNumCell.getStringCellValue();

                        if (specNum != null && !specNum.isEmpty()) {
                            // Check for duplicates
                            if (uniqueValues.contains(specNum)) {
                                // Assign a new unique code for duplicates
                                String newCode = generateUniqueCode();
                                // Update the "SPEC_NUM" cell with the new code
                                specNumCell.setCellValue(newCode);
                            } else {
                                // Map the column name to an attribute
                                String column = headerRow.getCell(specNumCell.getColumnIndex()).getStringCellValue();
                                String attribute = columnToAttributeMapping.get(column);
                                // Append the current year to the "SPEC_NUM" value
                                specNumCell.setCellValue(specNum + currentYear);
                                uniqueValues.add(specNum);
                            }
                        } else {
                            // Handle blank cells, assign a new unique code with the current year appended
                            String newCode = generateUniqueCode() + currentYear;
                            // Update the "SPEC_NUM" cell with the new code
                            specNumCell.setCellValue(newCode);
                        }
                    }
                }
            }
        }
    }

    private String generateUniqueCode() {
        return UUID.randomUUID().toString();
    }

    private void batchProcessedFiles(List<String> processedFilePaths) {
        try {
            String processedFilesFolderPath = Constants.PROCESSED_FILES_PATH;
            File destinationFolder = new File(processedFilesFolderPath);

            if (destinationFolder.exists() && destinationFolder.isDirectory()) {
                for (String filePath : processedFilePaths) {
                    File sourceFile = new File(filePath);

                    if (sourceFile.exists() && sourceFile.isFile()) {
                        File destinationFile = new File(destinationFolder, sourceFile.getName());

                        Files.move(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                        log.info("Parsed Files {} moved to {}", sourceFile.getName(), destinationFolder.getAbsolutePath());
                    } else {
                        log.error("Source file does not exist or is not a file: {}", filePath);
                    }
                }

                // Clear the files after batching
                processedFilePaths.clear();

                // formulate logging and post it to DHIS2 Datastore:

                // datastore end-points

            } else {
                log.error("Destination folder does not exist or is not a directory: {}", processedFilesFolderPath);
            }
        } catch (IOException e) {
            log.error("Error while moving files: {}", e.getMessage());
        }
    }
}