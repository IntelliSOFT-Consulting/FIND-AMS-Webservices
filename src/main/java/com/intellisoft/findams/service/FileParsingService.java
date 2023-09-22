package com.intellisoft.findams.service;

import com.intellisoft.findams.constants.Constants;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FileParsingService {
    private final HttpClientService httpClientService;
    private final List<String> processedFilePaths = new ArrayList<>();

    @Autowired
    public FileParsingService(HttpClientService httpClientService) {
        this.httpClientService = httpClientService;
    }

    public Disposable parseFile(String filePath, String fileContent) {
        List<JSONObject> records = parseFileContent(fileContent);

        JSONArray jsonArray = new JSONArray(records);

        // Post jsonArray to the DHIS2 API
        Mono<String> responseMono = httpClientService.postToDhis(jsonArray);

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

    private List<JSONObject> parseFileContent(String fileContent) {
        List<JSONObject> records = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new StringReader(fileContent))) {
            String line;
            boolean headerSkipped = false;
            String[] headers = null;

            while ((line = br.readLine()) != null) {
                if (!headerSkipped) {
                    headerSkipped = true;
                    headers = line.split("\\|");
                    continue;
                }

                String[] fields = line.split("\\|");
                JSONObject record = new JSONObject();

                for (int i = 0; i < headers.length; i++) {
                    if (i < fields.length) {
                        record.put(headers[i], fields[i]);
                    } else {
                        record.put(headers[i], "");
                    }
                }

                records.add(record);
            }
        } catch (IOException e) {
            log.error("Error while parsing file content", e);
        }

        return records;
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
            } else {
                log.error("Destination folder does not exist or is not a directory: {}", processedFilesFolderPath);
            }
        } catch (IOException e) {
            log.error("Error while moving files: {}", e.getMessage());
        }
    }
}