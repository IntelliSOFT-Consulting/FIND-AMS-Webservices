package com.intellisoft.findams.service;

import com.intellisoft.findams.constants.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Slf4j
@Service
public class FileParsingService {
    private final HttpClientService httpClientService;
    private final List<String> processedFilePaths = new ArrayList<>();

    @Autowired
    public FileParsingService(HttpClientService httpClientService) {
        this.httpClientService = httpClientService;
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

        // Convert Excel data to a JSON array
        List<JSONObject> jsonData = convertExcelToJsonArray(workbook);

        Mono<String> responseMono;

        try {
            // Post jsonArray to the DHIS2 API
            responseMono = httpClientService.postToDhis(jsonData);
        } finally {
            // Save the processed Excel file to a specific location
            try {
                FileOutputStream outputStream = new FileOutputStream("whonet/processed_output.xlsx");
                workbook.write(outputStream);
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return responseMono.subscribe(
                this::handleResponse,
                this::handleError,
                () -> {
                    processedFilePaths.add(filePath);
                    // Implement batching logic
                    batchProcessedFiles(processedFilePaths);
                }
        );
    }

    private List<JSONObject> convertExcelToJsonArray(Workbook workbook) {
        List<JSONObject> jsonData = new ArrayList<>();

        int columnIndex = -1;
        Row headerRow = workbook.getSheetAt(0).getRow(0);
        for (Cell cell : headerRow) {
            if (cell.getStringCellValue().equals("SPEC_NUM")) {
                columnIndex = cell.getColumnIndex();
                break;
            }
        }

        for (int i = 1; i <= workbook.getSheetAt(0).getLastRowNum(); i++) {
            Row row = workbook.getSheetAt(0).getRow(i);
            Cell specNumCell = row.getCell(columnIndex);

            if (specNumCell != null) {
                String specNum = specNumCell.getStringCellValue();
                if (specNum != null && !specNum.isEmpty()) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("SPEC_NUM", specNum);
                    jsonData.add(jsonObject);
                }
            }
        }

        return jsonData;
    }

    private void processExcelFile(Sheet sheet) {
        Set<String> uniqueValues = new HashSet<>();

        int columnIndex = -1;
        Row headerRow = sheet.getRow(0);

        // Search for the SPEC_NUM column
        for (Cell cell : headerRow) {
            if (cell.getStringCellValue().equals("SPEC_NUM")) {
                columnIndex = cell.getColumnIndex();
                break;
            }
        }

        // Get the current year
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);

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

    private void handleResponse(String response) {
        log.info("Response received from DHIS2: {}", response);
    }

    private void handleError(Throwable error) {
        log.error("Error occurred at DHIS2: {}", error.getMessage());
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