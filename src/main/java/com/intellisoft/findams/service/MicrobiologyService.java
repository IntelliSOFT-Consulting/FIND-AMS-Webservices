package com.intellisoft.findams.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellisoft.findams.constants.Constants;
import com.intellisoft.findams.dto.FileParseSummaryDto;
import com.intellisoft.findams.dto.TestTypeValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
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
import java.util.*;
import java.util.stream.Collectors;

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

                    processedFilePaths.add(filePath);
                    String uploadBatchNo = generateUniqueCode(); //unique batch applied to an upload
                    batchProcessedFiles(processedFilePaths, response, sheet, uploadBatchNo);
                });
            });
        }, error -> {
            log.debug("Error occurred while fetching attributes: {}", error.getMessage());
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
                            SimpleDateFormat outputDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                            Date date = inputDateFormat.parse(specDate);
                            specDateCell.setCellValue(outputDateFormat.format(date));
                        } catch (ParseException e) {
                            log.error(e.getMessage());
                        }
                    }
                }
            }
        }
    }


    private String generateUniqueCode() {
        return UUID.randomUUID().toString();
    }

    private void batchProcessedFiles(List<String> processedFilePaths, String apiResponse, Sheet sheet, String uploadBatchNo) {

        String processedFilesFolderPath = Constants.PROCESSED_FILES_PATH;
        File destinationFolder = new File(processedFilesFolderPath);

        try {
            if (destinationFolder.exists() && destinationFolder.isDirectory()) {
                for (String filePath : processedFilePaths) {
                    processFile(filePath, destinationFolder);
                }

                // Clear the files after batching
                processedFilePaths.clear();

                FileParseSummaryDto fileParseSummaryDto = parseApiResponse(apiResponse, sheet, uploadBatchNo);

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
        } else {
            log.error("Source file does not exist or is not a file: {}", filePath);
        }
    }

    private FileParseSummaryDto parseApiResponse(String apiResponse, Sheet sheet, String uploadBatchNo) throws JsonProcessingException {
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
                    fileParseSummaryDto.setConflictValues(conflictValues);
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

                            Mono<Map<String, Map<String, String>>> optionSetsMono = httpClientService.fetchOptionSets();
                            optionSetsMono.subscribe(optionSetsMap -> {

                                List<Map<String, Object>> dataValuesList = new ArrayList<>();

                                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                                    Row excelRow = sheet.getRow(i);

                                    int startIndex = 27;
                                    int endIndex = 83;

                                    for (int columnIndex = startIndex; columnIndex <= endIndex; columnIndex++) {
                                        Cell headerCell = sheet.getRow(0).getCell(columnIndex);
                                        String columnName = headerCell.getStringCellValue();

                                        Cell dataCell = excelRow.getCell(columnIndex);
                                        String cellValue = determineValue(dataCell);

                                        // If the cell value is "R", "S", or "I", determine the TestType & Culture Type
                                        if (cellValue.equals("R") || cellValue.equals("S") || cellValue.equals("I")) {
                                            TestTypeValue testTypeValue = determineTestType(cellValue);

                                            String testType = testTypeValue.getTestType();
                                            String cultureType = testTypeValue.getCultureType();

                                            // Determine AWARE classification:
                                            String awareClassification = extractAwareClassification(columnName);

                                            Map<String, Object> testTypeMap = new HashMap<>();
                                            testTypeMap.put("value", testType);
                                            testTypeMap.put("dataElement", Constants.TEST_TYPE_ID);
                                            dataValuesList.add(testTypeMap);

                                            Map<String, Object> antibioticsMap = new HashMap<>();
                                            antibioticsMap.put("value", columnName);
                                            antibioticsMap.put("dataElement", Constants.ANTIBIOTIC_ID);
                                            dataValuesList.add(antibioticsMap);

                                            Map<String, Object> awareMap = new HashMap<>();
                                            awareMap.put("value", awareClassification);
                                            awareMap.put("dataElement", Constants.AWARE_CLASSIFICATION);
                                            dataValuesList.add(awareMap);

                                            Map<String, Object> resultData = new HashMap<>();
                                            resultData.put("value", cultureType);
                                            resultData.put("dataElement", Constants.RESULT_ID);
                                            dataValuesList.add(resultData);

                                        }
                                    }
                                }

                                List<Map<String, Object>> filteredDataValues = dataValuesList.stream().filter(entry -> !entry.containsValue("N/A")).collect(Collectors.toList());

                                Map<String, Object> eventPayload = new HashMap<>();

                                eventPayload.put("dataValues", filteredDataValues);
                                eventPayload.put("program", Constants.WHONET_PROGRAM_ID);
                                eventPayload.put("programStage", Constants.WHONET_PROGRAM_STAGE_ID);
                                eventPayload.put("orgUnit", Constants.FIND_AMS_ORG_UNIT);
                                eventPayload.put("trackedEntityInstance", reference);
                                eventPayload.put("status", "COMPLETED");
                                eventPayload.put("eventDate", LocalDate.now().toString());
                                eventPayload.put("completedDate", LocalDate.now().toString());

                                try {
                                    httpClientService.postEventToDhis(objectMapper.writeValueAsString(eventPayload)).doOnError(error -> {
                                        log.debug("Error occurred while posting EVENT to DHIS2: {}", error.getMessage());
                                    }).subscribe(eventCreatedResponse -> {
                                    });
                                } catch (JsonProcessingException e) {
                                    throw new RuntimeException(e);
                                }
                            });

                        }, error -> {
                            log.debug("Error occurred while posting enrollment to DHIS2: {}", error.getMessage());
                        });
                    } catch (JsonProcessingException e) {
                        log.debug("Error while converting enrollment payload to JSON: {}", e.getMessage());
                    }
                }
            }

            fileParseSummaryDto.setConflictValues(conflictValues);

            return fileParseSummaryDto;
        } catch (Exception exp) {
            log.debug("Error while processing import summaries: {}", exp.getMessage());
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

    private String determineValue(Cell cell) {
        if (cell != null && cell.getCellType() == CellType.STRING) {
            String cellValue = cell.getStringCellValue();
            if (!cellValue.isEmpty() && (cellValue.equals("R") || cellValue.equals("S") || cellValue.equals("I"))) {
                return cellValue;
            }
        }
        return "";
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
            e.printStackTrace();
            return "ErrorReadingJsonFile";
        }
    }

}