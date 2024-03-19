package com.intellisoft.findams.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellisoft.findams.constants.Constants;
import com.intellisoft.findams.dto.FileParseSummaryDto;
import com.intellisoft.findams.dto.TestTypeValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class MicrobiologyService {
    private static final List<String> processedFilePaths = new ArrayList<>();
    private final HttpClientService httpClientService;
    private final Map<String, String> attributeToColumnMapping = initializeAttributeToColumnMapping();

    @Autowired
    public MicrobiologyService(HttpClientService httpClientService) {
        this.httpClientService = httpClientService;
    }

    private static Map<String, String> initializeAttributeToColumnMapping() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("Organism", "ORGANISM");
        mapping.put("Organism Type", "ORG_TYPE");
        mapping.put("Patient ID", "PATIENT_ID");
        mapping.put("First Name", "FIRST_NAME");
        mapping.put("Last Name", "LAST_NAME");
        mapping.put("Middle Name", "X_MIDDLE_N");
        mapping.put("Sex", "SEX");
        mapping.put("Age (Years)", "AGE");
        mapping.put("County", "X_COUNTY");
        mapping.put("Sub-county", "X_S_COUNTY");
        mapping.put("Diagnosis", "X_DIAGN");
        mapping.put("Patient Ward", "WARD");
        mapping.put("Department", "DEPARTMENT");
        mapping.put("Ward Type", "WARD_TYPE");
        mapping.put("Date of admission", "DATE_ADMIS");
        mapping.put("Specimen/sample Number", "SPEC_NUM");
        mapping.put("Isolate Number/Test", "ISOL_NUM");
        mapping.put("Spec collection date", "SPEC_DATE");
        mapping.put("Specimen Type", "SPEC_TYPE");
        mapping.put("Specimen source", "X_SOURCE");
        mapping.put("Method", "X_METHOD");
        return mapping;
    }

    public Disposable parseFile(String filePath, String fileContent, String fileName) throws IOException {

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
        Disposable subscription = httpClientService.fetchTrackedEntityAttributes().subscribe(attributesResponse -> {
            // Create a list to hold the main payload
            List<Map<String, Object>> trackedEntityInstances = new ArrayList<>();

            // Initialize attribute to columns mapping
            Map<String, String> attributeToColumnMapping = initializeAttributeToColumnMapping();

            Map<String, String> attributeIdMapping = createAttributeIdMapping(attributesResponse);

            // Fetch option sets
            Mono<Map<String, Map<String, String>>> optionSetsMono = httpClientService.fetchOptionSets();

            optionSetsMono.subscribe(optionSetsMap -> {
                // Iterate over the Excel data
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row excelRow = sheet.getRow(i);

                    List<String> rowValues = new ArrayList<>();

                    for (int j = 0; j < excelRow.getLastCellNum(); j++) {
                        Cell cell = excelRow.getCell(j);
                        if (cell != null) {
                            rowValues.add(cell.getStringCellValue());
                        }
                    }

                    int specDateColumnIndex = -1;
                    Row headerRow = sheet.getRow(0);
                    for (int k = 0; k < headerRow.getLastCellNum(); k++) {
                        Cell headerCell = headerRow.getCell(k);
                        if (headerCell != null && "SPEC_DATE".equals(headerCell.getStringCellValue())) {
                            specDateColumnIndex = k;
                            break;
                        }
                    }

                    for (int s = 1; s <= sheet.getLastRowNum(); s++) {
                        Row excelDocRow = sheet.getRow(s);

                        Cell specDateCell = excelDocRow.getCell(specDateColumnIndex);
                        String specDateValue = specDateCell.getStringCellValue();

                        // Create a payload for the tracked entity instance
                        Map<String, Object> trackedEntityInstance = new HashMap<>();
                        trackedEntityInstance.put("trackedEntityType", Constants.FIND_AMS_TRACKED_ENTITY_TYPE_ID);
                        trackedEntityInstance.put("orgUnit", Constants.FIND_AMS_ORG_UNIT);

                        List<Map<String, Object>> events = new ArrayList<>();
                        for (int j = 0; j < excelRow.getLastCellNum(); j++) {
                            Cell cell = excelRow.getCell(j);
                            if (cell != null) {
                                String cellValue = cell.getStringCellValue();
                                if (cellValue.equals("R") || cellValue.equals("S") || cellValue.equals("I")) {
                                    String columnName = sheet.getRow(0).getCell(j).getStringCellValue();
                                    List<Map<String, Object>> eventDataValues = new ArrayList<>();

                                    Map<String, Object> eventPayload1 = new HashMap<>();
                                    eventPayload1.put("dataElement", Constants.ANTIBIOTIC_ID);
                                    eventPayload1.put("value", columnName);
                                    eventDataValues.add(eventPayload1);

                                    Map<String, Object> eventPayload2 = new HashMap<>();
                                    eventPayload2.put("dataElement", Constants.AWARE_CLASSIFICATION);
                                    eventPayload2.put("value", extractAwareClassification(columnName));
                                    eventDataValues.add(eventPayload2);

                                    Map<String, Object> eventPayload3 = new HashMap<>();
                                    eventPayload3.put("dataElement", Constants.RESULT_ID);
                                    eventPayload3.put("value", determineTestType(cellValue).getCultureType());
                                    eventDataValues.add(eventPayload3);

                                    Map<String, Object> event = new HashMap<>();
                                    event.put("dataValues", eventDataValues);
                                    event.put("program", Constants.WHONET_PROGRAM_ID);
                                    event.put("programStage", Constants.WHONET_PROGRAM_STAGE_ID);
                                    event.put("orgUnit", Constants.FIND_AMS_ORG_UNIT);
                                    event.put("status", "COMPLETED");
                                    event.put("eventDate", specDateValue);
                                    event.put("completedDate", specDateValue);
                                    events.add(event);
                                }
                            }
                        }


                        Map<String, Object> enrollment = new HashMap<>();
                        enrollment.put("orgUnit", Constants.FIND_AMS_ORG_UNIT);
                        enrollment.put("program", Constants.WHONET_PROGRAM_ID);
                        enrollment.put("enrollmentDate", specDateValue);
                        enrollment.put("incidentDate", specDateValue);
                        enrollment.put("status", "COMPLETED");
                        enrollment.put("events", events);
                        trackedEntityInstance.put("enrollments", Collections.singletonList(enrollment));


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

                                // Map attribute values to option set codes
                                if (optionSetsMap.containsKey(attributeDisplayName)) {
                                    Map<String, String> optionsMap = optionSetsMap.get(attributeDisplayName);

                                    if (optionsMap.containsValue(cellValue)) {
                                        String finalCellValue = cellValue;
                                        String code = optionsMap.entrySet().stream().filter(optionEntry -> optionEntry.getValue().equals(finalCellValue)).map(Map.Entry::getKey).findFirst().orElse(cellValue);
                                        cellValue = code;
                                    }
                                } else {
                                    // Handling for special cases where the attribute display name doesn't match exactly with OptionSet name
                                    String closestMatch = optionSetsMap.keySet().stream().min(Comparator.comparing(a -> StringUtils.getJaroWinklerDistance(a.toLowerCase(), attributeDisplayName.toLowerCase()))).orElse(attributeDisplayName);

                                    if ("Organism".equalsIgnoreCase(attributeDisplayName)) {
                                        Map<String, String> optionsMap = optionSetsMap.get("Organism");
                                        if ("Candida paratropicalis".equalsIgnoreCase(cellValue)) {
                                            cellValue = "ctr";
                                        } else if ("Candida ravauti".equalsIgnoreCase(cellValue)) {
                                            cellValue = "cct";
                                        } else if ("Candida tropicalis".equalsIgnoreCase(cellValue)) {
                                            cellValue = "ctr";
                                        } else if (optionsMap.containsValue(cellValue)) {
                                            String finalCellValue = cellValue;
                                            String code = optionsMap.entrySet().stream().filter(optionEntry -> optionEntry.getValue().equals(finalCellValue)).map(Map.Entry::getKey).findFirst().orElse(cellValue);
                                            cellValue = code;
                                        }
                                    }

                                    if ("Specimen Type".equalsIgnoreCase(attributeDisplayName)) {
                                        Map<String, String> optionsMap = optionSetsMap.get("Specimens");
                                        if (optionsMap.containsValue(cellValue)) {
                                            String finalCellValue = cellValue;
                                            String code = optionsMap.entrySet().stream().filter(optionEntry -> optionEntry.getValue().equals(finalCellValue)).map(Map.Entry::getKey).findFirst().orElse(cellValue);
                                            cellValue = code;
                                        }
                                    } else if ("Department".equalsIgnoreCase(attributeDisplayName)) {
                                        Map<String, String> optionsMap = optionSetsMap.get("Wards");
                                        if (optionsMap.containsValue(cellValue)) {
                                            String finalCellValue = cellValue;
                                            String code = optionsMap.entrySet().stream().filter(optionEntry -> optionEntry.getValue().equals(finalCellValue)).map(Map.Entry::getKey).findFirst().orElse(cellValue);
                                            cellValue = code;
                                        } else {
                                            cellValue = "UKN";
                                        }
                                    } else if (optionSetsMap.containsKey(closestMatch)) {
                                        Map<String, String> optionsMap = optionSetsMap.get(closestMatch);
                                        if (optionsMap.containsValue(cellValue)) {
                                            String finalCellValue = cellValue;
                                            String code = optionsMap.entrySet().stream().filter(optionEntry -> optionEntry.getValue().equals(finalCellValue)).map(Map.Entry::getKey).findFirst().orElse(cellValue);
                                            cellValue = code;
                                        }
                                    }
                                }

                                Map<String, Object> attributeEntry = new HashMap<>();
                                attributeEntry.put("attribute", attributeId);
                                attributeEntry.put("value", cellValue);
                                attributesList.add(attributeEntry);
                            }
                        }


                        if (rowValues.contains("R") || rowValues.contains("S") || rowValues.contains("I")) {
                            Map<String, Object> testTypeMap = new HashMap<>();
                            testTypeMap.put("attribute", Constants.TEST_TYPE_ID);
                            testTypeMap.put("value", "Culture with AST");
                            attributesList.add(testTypeMap);
                        } else {
                            Map<String, Object> testTypeMap = new HashMap<>();
                            testTypeMap.put("attribute", Constants.TEST_TYPE_ID);
                            testTypeMap.put("value", "Culture without AST");
                            attributesList.add(testTypeMap);
                        }


                        // Add the attributesList to the trackedEntityInstance
                        trackedEntityInstance.put("attributes", attributesList);


                        // Add the trackedEntityInstance to the list of instances
                        trackedEntityInstances.add(trackedEntityInstance);
                    }
                }

                Map<String, Object> trackedEntityInstancePayload = new HashMap<>();
                trackedEntityInstancePayload.put("trackedEntityInstances", trackedEntityInstances);


                httpClientService.postTrackedEntityInstances(trackedEntityInstancePayload).doOnError(error -> {
                    log.error("Error occurred {}", error.getMessage());
                }).subscribe(response -> {
                    log.info("DHIS sever response {}", response);
                    // Implement batching logic for processed files
                    // send API response to send later to datastore

                    processedFilePaths.add(filePath);
                    String uploadBatchNo = generateUniqueCode(); //unique batch applied to an upload

                    // Get the current date & time
                    LocalDateTime now = LocalDateTime.now();

                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    String uploadDate = now.format(formatter);

                    batchProcessedFiles(processedFilePaths, response, sheet, uploadBatchNo, uploadDate, fileName);
                });
            });

        }, error -> {
            log.error("Error occurred while fetching attributes: {}", error.getMessage());
        });

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
            String column = cell.getStringCellValue();
            if (cell != null && cell.getStringCellValue().equalsIgnoreCase(columnName)) {
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

        int sexColumnIndex = -1;
        int specNumColumnIndex = -1;
        int specDateColumnIndex = -1;
        int organismColumnIndex = -1;
        int dateAdmissionColumnIndex = 1;

        // Find the columns for "SEX", "SPEC_NUM", and "SPEC_DATE"
        for (Cell cell : headerRow) {
            String columnName = cell.getStringCellValue();
            if (columnName.equals("SEX")) {
                sexColumnIndex = cell.getColumnIndex();
            } else if (columnName.equals("SPEC_NUM")) {
                specNumColumnIndex = cell.getColumnIndex();
            } else if (columnName.equals("SPEC_DATE")) {
                specDateColumnIndex = cell.getColumnIndex();
            } else if (columnName.equals("ORGANISM")) {
                organismColumnIndex = cell.getColumnIndex();
            } else if (columnName.equals("DATE_ADMIS")) {
                dateAdmissionColumnIndex = cell.getColumnIndex();
            }
        }

        // Get the current year
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);

        // Process the Excel sheet using the mapping
        if (specNumColumnIndex >= 0 && specDateColumnIndex >= 0) {
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (row != null) {

                    Cell sexCell = row.getCell(sexColumnIndex);
                    String sexValue = sexCell.getStringCellValue();

                    if ("m".equalsIgnoreCase(sexValue)) {
                        sexCell.setCellValue("Male");
                    } else if ("f".equalsIgnoreCase(sexValue)) {
                        sexCell.setCellValue("Female");
                    } else {
                        sexCell.setCellValue("Other");
                    }

                    // Process SPEC_NUM column
                    Cell specNumCell = row.getCell(specNumColumnIndex);
                    String specNum = specNumCell.getStringCellValue();

                    // Process SPEC_DATE column
                    Cell specDateCell = row.getCell(specDateColumnIndex);
                    String specDate = specDateCell.getStringCellValue();

                    // Process DATE_ADMIS column
                    Cell dateAdmissionCell = row.getCell(dateAdmissionColumnIndex);
                    String dateAdmission = dateAdmissionCell.getStringCellValue();

                    // Process ORGANISM column
                    Cell organismCell = row.getCell(organismColumnIndex);
                    String organism = organismCell.getStringCellValue();

                    if (specNum != null && !specNum.isEmpty()) {
                        if (uniqueValues.contains(specNum)) {
                            // Assign a new unique code for duplicates
                            String newCode = generateUniqueCode();
                            // Update the "SPEC_NUM" cell with the new code
                            specNumCell.setCellValue(newCode);
                        } else {
                            // Map the column name to an attribute
                            String column = headerRow.getCell(specNumColumnIndex).getStringCellValue();
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

                    // Format the SPEC_DATE column to YYYY-MM-DD
                    if (specDate != null && !specDate.isEmpty()) {
                        try {
                            SimpleDateFormat inputDateFormat = new SimpleDateFormat("dd/M/yyyy hh:mm:ss a", Locale.ENGLISH);
                            SimpleDateFormat alternativeInputDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH);
                            SimpleDateFormat outputDateFormat = new SimpleDateFormat("yyyy-MM-dd");

                            Date date = null;

                            try {
                                date = inputDateFormat.parse(specDate);
                            } catch (ParseException e1) {
                                try {
                                    date = alternativeInputDateFormat.parse(specDate);
                                } catch (ParseException e2) {
                                    log.error("Error occurred while parsing date");
                                }
                            }

                            if (date != null) {
                                specDateCell.setCellValue(outputDateFormat.format(date));
                            }

                        } catch (Exception e) {
                            log.error("Error occurred while parsing date", e);
                        }
                    }


                    // Format the DATE_ADMIS column to YYYY-MM-DD
                    if (dateAdmission != null && !dateAdmission.isEmpty()) {
                        try {
                            SimpleDateFormat inputDateFormat = new SimpleDateFormat("dd/M/yyyy hh:mm:ss a", Locale.ENGLISH);
                            SimpleDateFormat alternativeInputDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH);
                            SimpleDateFormat outputDateFormat = new SimpleDateFormat("yyyy-MM-dd");

                            Date date = null;

                            try {
                                date = inputDateFormat.parse(dateAdmission);
                            } catch (ParseException dateAdmissionEx) {
                                try {
                                    date = alternativeInputDateFormat.parse(dateAdmission);
                                } catch (ParseException dateAdmissionEx2) {
                                    log.error("Error occurred while parsing date");
                                }
                            }

                            if (date != null) {
                                dateAdmissionCell.setCellValue(outputDateFormat.format(date));
                            }

                        } catch (Exception e) {
                            log.error("Error occurred while parsing date", e);
                        }
                    }

                }
            }
        }
    }


    private String generateUniqueCode() {
        return UUID.randomUUID().toString();
    }

    private void batchProcessedFiles(List<String> processedFilePaths, String apiResponse, Sheet sheet, String uploadBatchNo, String uploadDate, String fileName) {

        String processedFilesFolderPath = Constants.PROCESSED_FILES_PATH;
        File destinationFolder = new File(processedFilesFolderPath);

        try {
            if (destinationFolder.exists() && destinationFolder.isDirectory()) {
                for (String filePath : processedFilePaths) {
                    processFile(filePath, destinationFolder);
                }

                // Clear the files after batching
                processedFilePaths.clear();

                FileParseSummaryDto fileParseSummaryDto = parseApiResponse(apiResponse, sheet, uploadBatchNo, uploadDate, fileName);

                httpClientService.postToDhis2DataStore(fileParseSummaryDto).subscribe();

            } else {
                log.error("Destination folder does not exist or is not a directory: {}", processedFilesFolderPath);
            }
        } catch (IOException e) {
            log.error("Error while moving files");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void processFile(String filePath, File destinationFolder) throws IOException {
        File sourceFile = new File(filePath);

        if (sourceFile.exists() && sourceFile.isFile()) {
            File destinationFile = new File(destinationFolder, sourceFile.getName());
            Files.move(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            log.error("Source file does not exist or is not a file: {}", filePath);
        }
    }

    private FileParseSummaryDto parseApiResponse(String apiResponse, Sheet sheet, String uploadBatchNo, String uploadDate, String fileName) throws JsonProcessingException {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(apiResponse);

            FileParseSummaryDto fileParseSummaryDto = new FileParseSummaryDto();

            JsonNode responseNode = jsonNode.path("response");

            String responseType = responseNode.path("responseType").asText();
            String status = responseNode.path("status").asText();
            int imported = responseNode.path("imported").asInt();
            int updated = responseNode.path("updated").asInt();
            int ignored = responseNode.path("ignored").asInt();
            int deleted = responseNode.path("deleted").asInt();

            fileParseSummaryDto.setResponseType(responseType);
            fileParseSummaryDto.setStatus(status);
            fileParseSummaryDto.setImported(imported);
            fileParseSummaryDto.setUpdated(updated);
            fileParseSummaryDto.setDeleted(deleted);
            fileParseSummaryDto.setIgnored(ignored);
            fileParseSummaryDto.setBatchNo(uploadBatchNo);
            fileParseSummaryDto.setUploadDate(uploadDate);
            fileParseSummaryDto.setFileName(fileName);


            // formulate a payload to send to Enrollment API ON DHIS:>>>>>>
            JsonNode importSummariesNode = jsonNode.path("response").path("importSummaries");

            List<String> conflictValues = new ArrayList<>();
            for (JsonNode summaryNode : importSummariesNode) {
                String importSummaryStatus = summaryNode.path("status").asText();

                JsonNode conflicts = summaryNode.path("conflicts");
                for (JsonNode conflict : conflicts) {
                    String conflictValue = conflict.path("value").asText();
                    conflictValues.add(conflictValue);
                }
            }


            fileParseSummaryDto.setConflictValues(conflictValues);

            return fileParseSummaryDto;
        } catch (Exception exp) {
            log.error("Error while processing import summaries");
        }
        return null;
    }

    public TestTypeValue determineTestType(String cellValue) {
        if (cellValue.equals("R") || cellValue.equals("S") || cellValue.equals("I")) {
            return new TestTypeValue("Culture with AST", cellValue);
        } else {
            return new TestTypeValue("Culture without AST", "N/A");
        }
    }

    private String extractAwareClassification(String columnName) {
        String awareDataPath = Constants.TESTS_PATH + "aware.json";

        try {
            String jsonContent = new String(Files.readAllBytes(Paths.get(awareDataPath)));

            JSONArray jsonArray = new JSONArray(jsonContent);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject entry = jsonArray.getJSONObject(i);
                if (columnName.equals(entry.getString("drug_code"))) {
                    return entry.getString("aware_classification");
                }
            }

            return "Unknown";
        } catch (IOException e) {
            log.error("IO Error occurred while Reading JSON file");
            return "ErrorReadingJsonFile";
        }
    }

}