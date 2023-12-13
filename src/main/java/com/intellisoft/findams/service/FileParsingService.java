package com.intellisoft.findams.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellisoft.findams.constants.Constants;
import com.intellisoft.findams.dto.FileParseSummaryDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
public class FileParsingService {
    private static final List<String> processedFilePaths = new ArrayList<>();
    private final HttpClientService httpClientService;
    private final Map<String, String> attributeToColumnMapping = initializeAttributeToColumnMapping();

    @Autowired
    public FileParsingService(HttpClientService httpClientService) {
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
        mapping.put("Test Type", "TEST_TYPE");
        mapping.put("AWaRe Classification", "AWARE");
        return mapping;
    }

    private static Map<String, String> initializeEventAttributeToColumnMapping() {
        Map<String, String> eventMapping = new HashMap<>();
        eventMapping.put("AMC_ND20", "AMC_ND20");
        eventMapping.put("AMK_ND30", "AMK_ND30");
        eventMapping.put("AMP_ND10", "AMP_ND10");
        eventMapping.put("ATM_ND30", "ATM_ND30");
        eventMapping.put("AZM_ND15", "AZM_ND15");
        eventMapping.put("CAZ_ND30", "CAZ_ND30");
        eventMapping.put("CHL_ND30", "CHL_ND30");
        eventMapping.put("CIP_ND5", "CIP_ND5");
        eventMapping.put("CEP_ND30", "CEP_ND30");
        eventMapping.put("CLI_ND2", "CLI_ND2");
        eventMapping.put("COL_ND10", "COL_ND10");
        eventMapping.put("CRB_ND100", "CRB_ND100");
        eventMapping.put("CRO_ND30", "CRO_ND30");
        eventMapping.put("CTX_ND30", "CTX_ND30");
        eventMapping.put("CTX_NM", "CTX_NM");
        eventMapping.put("CXM_ND30", "CXM_ND30");
        eventMapping.put("CZX_ND30", "CZX_ND30");
        eventMapping.put("DOX_ND30", "DOX_ND30");
        eventMapping.put("ERY_ND15", "ERY_ND15");
        eventMapping.put("ETP_ND10", "ETP_ND10");
        eventMapping.put("FEP_ND30", "FEP_ND30");
        eventMapping.put("FOX_ND30", "FOX_ND30");
        eventMapping.put("GEN_ND10", "GEN_ND10");
        eventMapping.put("IPM_ND10", "IPM_ND10");
        eventMapping.put("LVX_ND5", "LVX_ND5");
        eventMapping.put("MAN_ND30", "MAN_ND30");
        eventMapping.put("MEM_ND10", "MEM_ND10");
        eventMapping.put("MEZ_ND75", "MEZ_ND75");
        eventMapping.put("MNO_ND30", "MNO_ND30");
        eventMapping.put("MTR_ND5", "MTR_ND5");
        eventMapping.put("NAL_ND30", "NAL_ND30");
        eventMapping.put("NIT_ND300", "NIT_ND300");
        eventMapping.put("NOR_ND10", "NOR_ND10");
        eventMapping.put("NOV_ND5", "NOV_ND5");
        eventMapping.put("OFX_ND5", "OFX_ND5");
        eventMapping.put("OXA_ND1", "OXA_ND1");
        eventMapping.put("PEN_ND10", "PEN_ND10");
        eventMapping.put("PEN_NE", "PEN_NE");
        eventMapping.put("PEN_NM", "PEN_NM");
        eventMapping.put("PIP_ND100", "PIP_ND100");
        eventMapping.put("PNV_ND10", "PNV_ND10");
        eventMapping.put("RIF_ND5", "RIF_ND5");
        eventMapping.put("SSS_ND200", "SSS_ND200");
        eventMapping.put("STR_ND10", "STR_ND10");
        eventMapping.put("SXT_ND1_2", "SXT_ND1_2");
        eventMapping.put("TCC_ND75", "TCC_ND75");
        eventMapping.put("TCY_ND30", "TCY_ND30");
        eventMapping.put("TEC_ND30", "TEC_ND30");
        eventMapping.put("TGC_ND15", "TGC_ND15");
        eventMapping.put("TIC_ND75", "TIC_ND75");
        eventMapping.put("TOB_ND10", "TOB_ND10");
        eventMapping.put("TZP_ND100", "TZP_ND100");
        eventMapping.put("VAN_ND30", "VAN_ND30");
        eventMapping.put("VAN_NE", "VAN_NE");
        eventMapping.put("VAN_NM", "VAN_NM");
        eventMapping.put("X_1_ND", "X_1_ND");
        eventMapping.put("X_2_ND", "X_2_ND");
        eventMapping.put("Organism", "Organism");
        eventMapping.put("Organism type", "Organism type");
        eventMapping.put("Isolate Number/Test", "Isolate Number/Test");
        return eventMapping;
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

            // Adding new columns to hold test_types and AWARE
            String[] newColumnNames = {"TEST_TYPE", "AWARE"};
            for (String newColumnName : newColumnNames) {
                Cell newColumnCell = row.createCell(row.getLastCellNum());
                newColumnCell.setCellValue(newColumnName);
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
                // Iterate over rows to determine and append "Test Type"
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row excelRow = sheet.getRow(i);

                    // Create a payload for the tracked entity instance
                    Map<String, Object> trackedEntityInstance = new HashMap<>();
                    trackedEntityInstance.put("trackedEntityType", Constants.FIND_AMS_TRACKED_ENTITY_TYPE_ID);
                    trackedEntityInstance.put("orgUnit", Constants.FIND_AMS_ORG_UNIT);

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

                                if ("Specimen Type".equalsIgnoreCase(attributeDisplayName)) {
                                    Map<String, String> optionsMap = optionSetsMap.get("Specimens");
                                    if (optionsMap.containsValue(cellValue)) {
                                        String finalCellValue = cellValue;
                                        String code = optionsMap.entrySet().stream().filter(optionEntry -> optionEntry.getValue().equals(finalCellValue)).map(Map.Entry::getKey).findFirst().orElse(cellValue);
                                        cellValue = code;
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

                    // Determine culture type for the current row
                    String cultureType = determineCultureType(excelRow, getColumnIndex(sheet.getRow(0), "X_AMT"));

                    String testTypeAttributeName = "Test Type";
                    String testTypeColumnMapping = attributeToColumnMapping.get(testTypeAttributeName);
                    int testTypeColumnIndex = getColumnIndex(sheet.getRow(0), testTypeColumnMapping);

                    if (testTypeColumnIndex >= 0) {
                        String testTypeAttributeValue = attributeIdMapping.get(testTypeAttributeName);
                        Map<String, Object> testTypeAttributeEntry = new HashMap<>();
                        testTypeAttributeEntry.put("attribute", testTypeAttributeValue);
                        testTypeAttributeEntry.put("value", cultureType);

                        attributesList.add(testTypeAttributeEntry);
                    }

                    List<Map<String, Object>> awareAttributesList = new ArrayList<>();

                    // AwaRe Classification:
                    Mono<List<Map<String, String>>> apiResponseMono = httpClientService.fetchAwareClassification();

                    // Handle errors if any
                    apiResponseMono.subscribe(apiResponseList -> {
                        // Iterate over columns
                        for (int columnIndex = getColumnIndex(sheet.getRow(0), "PIP_ND100"); columnIndex <= getColumnIndex(sheet.getRow(0), "PEN_NE"); columnIndex++) {
                            Cell headerCell = sheet.getRow(0).getCell(columnIndex);
                            String columnName = headerCell.getStringCellValue();
                            log.info("aware columnName {}", columnName);

                            // Extract AWaRe Classification from the API response based on the column name
                            String awareClassification = extractAwareClassification(columnName, apiResponseList);
                            System.out.println("awareClassification" + awareClassification);

                            String awareAttributeName = "AWaRe Classification";
                            String awareColumnMapping = attributeToColumnMapping.get(awareAttributeName);
                            int awareColumnIndex = getColumnIndex(sheet.getRow(0), awareColumnMapping);

                            if (awareColumnIndex >= 0) {
                                String awareAttributeValue = attributeIdMapping.get(awareAttributeName);
                                Map<String, Object> awareAttributeEntry = new HashMap<>();
                                awareAttributeEntry.put("attribute", awareAttributeValue);
                                awareAttributeEntry.put("value", awareClassification);
                                attributesList.add(awareAttributeEntry);
                            }
                        }
                    }, Throwable::printStackTrace);

                    trackedEntityInstance.put("attributes", awareAttributesList);
                    trackedEntityInstances.add(trackedEntityInstance);

                    // Add the attributesList to the trackedEntityInstance
                    trackedEntityInstance.put("attributes", attributesList);

                    // Add the trackedEntityInstance to the list of instances
                    trackedEntityInstances.add(trackedEntityInstance);
                }

                Map<String, Object> trackedEntityInstancePayload = new HashMap<>();
                trackedEntityInstancePayload.put("trackedEntityInstances", trackedEntityInstances);

                httpClientService.postTrackedEntityInstances(trackedEntityInstancePayload).doOnError(error -> {
                    log.error("Error occurred {}", error.getMessage());
                }).subscribe(response -> {
                    // Implement batching logic for processed files
                    // send API response to send later to datastore
                    removeColumns(sheet, "TEST_TYPE", "AWARE");
                    processedFilePaths.add(filePath);
                    batchProcessedFiles(processedFilePaths, response, sheet);
                });
            });
        }, error -> {
            log.error("Error occurred while fetching attributes: {}", error.getMessage());
        });

        return subscription;
    }

    private void removeColumns(Sheet sheet, String... columnNames) {
        for (String columnName : columnNames) {
            int columnIndex = getColumnIndex(sheet.getRow(0), columnName);

            if (columnIndex >= 0) {
                for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row != null) {
                        Cell cell = row.getCell(columnIndex);
                        if (cell != null) {
                            row.removeCell(cell);
                        }
                    }
                }
            }
        }
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

    private Map<String, String> createEventsAttributeIdMapping() throws IOException {
        Map<String, String> attributeIdMapping = new HashMap<>();

        String filePath = Constants.TESTS_PATH + "tests.json";

        // Parse JSON file
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(new File(filePath));

        // Iterate over JSON array and populate the map
        for (JsonNode node : jsonNode) {
            String displayName = node.get("displayName").asText();
            String id = node.get("id").asText();
            attributeIdMapping.put(displayName, id);
        }
        return attributeIdMapping;
    }


    private int getColumnIndex(Row headerRow, String columnName) {
        for (int j = 0; j < headerRow.getLastCellNum(); j++) {
            Cell cell = headerRow.getCell(j);
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

        // Find the columns for "SEX", "SPEC_NUM", and "SPEC_DATE"
        for (Cell cell : headerRow) {
            String columnName = cell.getStringCellValue();
            if (columnName.equals("SEX")) {
                sexColumnIndex = cell.getColumnIndex();
            } else if (columnName.equals("SPEC_NUM")) {
                specNumColumnIndex = cell.getColumnIndex();
            } else if (columnName.equals("SPEC_DATE")) {
                specDateColumnIndex = cell.getColumnIndex();
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
                    }

                    // Process SPEC_NUM column
                    Cell specNumCell = row.getCell(specNumColumnIndex);
                    String specNum = specNumCell.getStringCellValue();

                    // Process SPEC_DATE column
                    Cell specDateCell = row.getCell(specDateColumnIndex);
                    String specDate = specDateCell.getStringCellValue();

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
                            SimpleDateFormat outputDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                            Date date = inputDateFormat.parse(specDate);
                            specDateCell.setCellValue(outputDateFormat.format(date));
                        } catch (ParseException e) {
                            log.error(e.getMessage());
                        }
                    }

                    // Determine culture type
                    String cultureType = determineCultureType(row, getColumnIndex(sheet.getRow(0), "X_AMT"));
                    Cell cultureTypeCell = row.createCell(headerRow.getLastCellNum());
                    cultureTypeCell.setCellValue(cultureType);
                    log.info("Row {}: Culture Type: {}", i, cultureType);
                }
            }
        }
    }

    private String extractAwareClassification(String columnName, List<Map<String, String>> apiResponse) {
        System.out.println("Column Name: " + columnName);
        for (Map<String, String> entry : apiResponse) {
            String drugCode = entry.get("drug_code");

            if (columnName.equalsIgnoreCase(drugCode)) {
                System.out.println("matched");
                return entry.get("aware_classification");
            }
        }
        return "Unknown";
    }


    private String determineCultureType(Row row, int startColumnIndex) {
        for (int columnIndex = startColumnIndex; columnIndex < row.getLastCellNum(); columnIndex++) {
            String cellValue = getCellValue(row, columnIndex);

            if (columnIndex == startColumnIndex && !cellValue.equalsIgnoreCase("X_AMT")) {
                continue;
            }

            if ("R".equals(cellValue) || "S".equals(cellValue) || "I".equals(cellValue)) {
                return "Culture with AST";
            }
        }
        return "Culture without AST";
    }


    private String generateUniqueCode() {
        return UUID.randomUUID().toString();
    }

    private void batchProcessedFiles(List<String> processedFilePaths, String apiResponse, Sheet sheet) {

        String processedFilesFolderPath = Constants.PROCESSED_FILES_PATH;
        File destinationFolder = new File(processedFilesFolderPath);

        try {
            if (destinationFolder.exists() && destinationFolder.isDirectory()) {
                for (String filePath : processedFilePaths) {
                    processFile(filePath, destinationFolder);
                }

                // Clear the files after batching
                processedFilePaths.clear();

                FileParseSummaryDto fileParseSummaryDto = parseApiResponse(apiResponse, sheet);

                httpClientService.postToDhis2DataStore(fileParseSummaryDto).subscribe();

            } else {
                log.error("Destination folder does not exist or is not a directory: {}", processedFilesFolderPath);
            }
        } catch (IOException e) {
            log.error("Error while moving files: {}", e.getMessage());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void processFile(String filePath, File destinationFolder) throws IOException {
        File sourceFile = new File(filePath);

        if (sourceFile.exists() && sourceFile.isFile()) {
            File destinationFile = new File(destinationFolder, sourceFile.getName());
            Files.move(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.info("Parsed Files {} moved to {}", sourceFile.getName(), destinationFolder.getAbsolutePath());
        } else {
            log.error("Source file does not exist or is not a file: {}", filePath);
        }
    }

    private FileParseSummaryDto parseApiResponse(String apiResponse, Sheet sheet) throws JsonProcessingException {
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
            fileParseSummaryDto.setBatchNo(generateUniqueCode());

            log.info("fileParseSummaryDto {}", fileParseSummaryDto);

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

                if (importSummaryStatus.equals("ERROR")) {
                    return fileParseSummaryDto;
                } else {
                    String reference = summaryNode.path("reference").asText();

                    // create an enrolment payload
                    LocalDate enrollmentDate = LocalDate.now();
                    LocalDate incidentDate = LocalDate.now();

                    // Build the enrollment payload
                    Map<String, Object> enrollmentPayload = new HashMap<>();
                    enrollmentPayload.put("trackedEntityInstance", reference);
                    enrollmentPayload.put("program", Constants.WHONET_PROGRAM_ID);
                    enrollmentPayload.put("status", "ACTIVE");
                    enrollmentPayload.put("orgUnit", Constants.FIND_AMS_ORG_UNIT);
                    enrollmentPayload.put("enrollmentDate", enrollmentDate.toString());
                    enrollmentPayload.put("incidentDate", incidentDate.toString());

                    String enrollmentReqPayload = null;

                    try {
                        enrollmentReqPayload = objectMapper.writeValueAsString(enrollmentPayload);

                        // send to DHIS2 enrollment API::
                        Disposable subscription = httpClientService.postEnrollmentToDhis(enrollmentReqPayload).subscribe(enrollmentResponse -> {

                            JsonNode enrollmentJsonNode;
                            try {
                                enrollmentJsonNode = objectMapper.readTree(enrollmentResponse);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }

                            JsonNode enrollmentSummariesNode = enrollmentJsonNode.path("response").path("importSummaries");

                            String enrollmentReference = null;
                            LocalDate dueDate = null;


                            for (JsonNode node : enrollmentSummariesNode) {

                                enrollmentReference = summaryNode.path("reference").asText();

                                dueDate = LocalDate.now();
                            }

                            String finalEnrollmentReference = enrollmentReference;
                            LocalDate finalDueDate = dueDate;


                            // Initialize attribute to columns mapping
                            Map<String, String> attributeToColumnMapping = initializeEventAttributeToColumnMapping();

                            Map<String, String> attributeIdMapping = null;
                            try {
                                attributeIdMapping = createEventsAttributeIdMapping();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }

                            Mono<Map<String, Map<String, String>>> optionSetsMono = httpClientService.fetchOptionSets();
                            List<Map<String, String>> eventsList = new ArrayList<>();

                            Map<String, String> finalAttributeIdMapping = attributeIdMapping;
                            optionSetsMono.subscribe(optionSetsMap -> {
                                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                                    Row excelRow = sheet.getRow(i);

                                    // Create a payload for empty event
                                    Map<String, String> eventPayload = new HashMap<>();

                                    // Add fixed values to the payload
                                    eventPayload.put("trackedEntityInstance", reference);
                                    eventPayload.put("program", Constants.WHONET_PROGRAM_ID);
                                    eventPayload.put("programStage", Constants.WHONET_PROGRAM_STAGE_ID);
                                    eventPayload.put("orgUnit", Constants.FIND_AMS_ORG_UNIT);
                                    eventPayload.put("status", "SCHEDULE");
                                    eventPayload.put("dueDate", String.valueOf(finalDueDate));
                                    eventPayload.put("enrollment", finalEnrollmentReference);

                                    // Add dynamic values from Excel to the payload
                                    for (Map.Entry<String, String> entry : attributeToColumnMapping.entrySet()) {
                                        String attributeName = entry.getKey();
                                        String columnMapping = entry.getValue();
                                        int columnIndex = getColumnIndex(sheet.getRow(0), columnMapping);

                                        if (columnIndex != -1) {
                                            String cellValue = getCellValue(excelRow, columnIndex);
                                            String attributeId = finalAttributeIdMapping.get(attributeName);

                                            // Map attribute values to option set codes
                                            if (optionSetsMap.containsKey(attributeName)) {
                                                Map<String, String> optionsMap = optionSetsMap.get(attributeName);

                                                if (optionsMap.containsValue(cellValue)) {
                                                    String finalCellValue = cellValue;
                                                    String code = optionsMap.entrySet().stream().filter(optionEntry -> optionEntry.getValue().equals(finalCellValue)).map(Map.Entry::getKey).findFirst().orElse(cellValue);
                                                    cellValue = code;
                                                }
                                            }

                                            if (attributeId != null && cellValue != null) {
                                                eventPayload.put(attributeId, cellValue);
                                                log.info("Attribute: {}, AttributeId: {}, CellValue: {}", attributeName, attributeId, cellValue);
                                            }
                                        }
                                    }

                                    // Add the event payload to the list of events
                                    eventsList.add(eventPayload);
                                }

                                // Construct the final payload with all events
                                Map<String, List<Map<String, String>>> finalPayload = new HashMap<>();
                                finalPayload.put("events", eventsList);

                                //send to events api:
                                try {
                                    List<Map<String, String>> finalEventsList = eventsList;
                                    httpClientService.postEventToDhis(finalPayload).doOnError(error -> {

                                    }).subscribe(eventCreatedResponse -> {

                                        JsonNode eventJsonNode;
                                        try {
                                            eventJsonNode = objectMapper.readTree(eventCreatedResponse);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }

                                        JsonNode eventSummariesNode = eventJsonNode.path("response").path("importSummaries");
                                        String eventReference = null;
                                        for (JsonNode node : eventSummariesNode) {
                                            eventReference = node.path("reference").asText();
                                        }

                                        String trackedEntity = reference;
                                        Map<String, Object> eventUpdatePayload = new HashMap<>();
                                        eventUpdatePayload.put("event", eventReference);
                                        eventUpdatePayload.put("orgUnit", Constants.FIND_AMS_ORG_UNIT);
                                        eventUpdatePayload.put("program", Constants.WHONET_PROGRAM_ID);
                                        eventUpdatePayload.put("programStage", Constants.WHONET_PROGRAM_STAGE_ID);
                                        eventUpdatePayload.put("status", "ACTIVE");
                                        eventUpdatePayload.put("trackedEntityInstance", trackedEntity);

                                        List<Map<String, Object>> dataValues = new ArrayList<>();

                                        assert finalEventsList != null;

                                        for (Map<String, String> eventData : finalEventsList) {
                                            for (Map.Entry<String, String> entry : eventData.entrySet()) {
                                                Map<String, Object> dataValue = new HashMap<>();
                                                dataValue.put("dataElement", entry.getKey());
                                                dataValue.put("value", entry.getValue());

                                                dataValue.put("providedElsewhere", false);

                                                dataValues.add(dataValue);
                                            }
                                        }

                                        // Remove unwanted entries from dataValues
                                        String[] elementsToRemove = {"dueDate", "program", "orgUnit", "status", "trackedEntityInstance", "programStage", "enrollment"};
                                        String additionalElementToRemove = "TEST_TYPE";
                                        String extraElementToRemove = "AWARE";

                                        Iterator<Map<String, Object>> iterator = dataValues.iterator();
                                        while (iterator.hasNext()) {
                                            Map<String, Object> dataValue = iterator.next();

                                            String dataElement = (String) dataValue.getOrDefault("dataElement", dataValue.getOrDefault("TEST_TYPE", dataValue.get("AWARE")));

                                            String valueField = (String) dataValue.get("value");

                                            if (valueField != null && (Arrays.asList(elementsToRemove).contains(valueField) || additionalElementToRemove.equals(valueField) || extraElementToRemove.equals(valueField))) {
                                                iterator.remove();
                                            } else if (Arrays.asList(elementsToRemove).contains(dataElement) || additionalElementToRemove.equals(dataElement) || extraElementToRemove.equals(dataElement)) {
                                                iterator.remove();
                                            }
                                        }


                                        for (String element : elementsToRemove) {
                                            dataValues.removeIf(dataValue -> element.equals(dataValue.get("dataElement")));
                                        }

                                        eventUpdatePayload.put("dataValues", dataValues);

                                        try {
                                            httpClientService.updateEventOnDhis(eventUpdatePayload).doOnError(error -> {
                                                log.error("Error occurred {}", error.getMessage());
                                            }).subscribe(eventUpdatedResponse -> {
                                                log.info("eventUpdatedResponse {}", eventUpdatedResponse);
                                            });
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    });
                                } catch (JsonProcessingException e) {
                                    throw new RuntimeException(e);
                                }

                            });
                        }, error -> {
                            log.error("Error occurred while posting enrollment to DHIS2: {}", error.getMessage());
                        });
                    } catch (JsonProcessingException e) {
                        log.error("Error while converting enrollment payload to JSON: {}", e.getMessage());
                    }
                }
            }
            // Set conflict values in the DTO
            fileParseSummaryDto.setConflictValues(conflictValues);
            System.out.println("conflictValue" +conflictValues);

            return fileParseSummaryDto;
        } catch (Exception exp) {
            log.error("Error while processing import summaries: {}", exp.getMessage());
        }
        return null;
    }
}