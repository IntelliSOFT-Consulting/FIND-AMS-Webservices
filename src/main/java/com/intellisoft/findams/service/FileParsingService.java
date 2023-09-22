package com.intellisoft.findams.service;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FileParsingService {

    private final HttpClientService httpClientService;

    @Autowired
    public FileParsingService(HttpClientService httpClientService) {
        this.httpClientService = httpClientService;
    }

    public Disposable parseFile(String filePath, String fileContent) {
        List<JSONObject> records = parseFileContent(fileContent);

        JSONArray jsonArray = new JSONArray(records);

        // Call the service method from HttpClientService to post jsonArray to the dhis2 API
        Mono<String> responseMono = httpClientService.postToDhis(jsonArray);

        return responseMono.subscribe(
                this::handleResponse,
                this::handleError
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
                        record.put(headers[i], ""); // Handle missing fields
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
        // Handle the response here
        log.info("Response received from DHIS2: {}", response);
    }

    private void handleError(Throwable error) {
        // Handle any errors that occurred during the request
        log.error("Error occurred at DHIS2: {}", error.getMessage());
    }
}
