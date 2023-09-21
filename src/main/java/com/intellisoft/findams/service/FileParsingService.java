package com.intellisoft.findams.service;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FileParsingService {
    public String parseFile(String filePath) {
        List<JSONObject> records = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean headerSkipped = false;
            String[] headers = null;

            while ((line = br.readLine()) != null) {
                if (!headerSkipped) {
                    // Skip the header line
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
            e.printStackTrace();
        }

        // Convert the list of JSON objects to a JSON array
        JSONArray jsonArray = new JSONArray(records);

        log.info("Parse file in JSON -> {}", jsonArray);

        return jsonArray.toString();
    }
}
