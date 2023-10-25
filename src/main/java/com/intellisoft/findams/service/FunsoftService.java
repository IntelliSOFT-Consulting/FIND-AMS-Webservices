package com.intellisoft.findams.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class FunsoftService {

    @Autowired
    HttpClientService httpClientService;

    @Value("${ams.funsoft.atc-ddd-index-url}")
    private String atcDddIndexUrl;

    public void getPatientsAntibioticPrescriptions() {

        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String patientId = "";
        //        String startDate = today.format(formatter);
        //        String endDate = today.format(formatter);

        String startDate = "2022-04-28";
        String endDate = "2023-09-28";

        httpClientService.getPatientsAntibioticPrescriptions(patientId, startDate, endDate)
                .subscribe(
                        response -> {
                            try {
                                ObjectMapper objectMapper = new ObjectMapper();
                                JsonNode jsonNode = objectMapper.readTree(response);

                                JsonNode patients = jsonNode.path("patients");
                                for (JsonNode patient : patients) {
                                    JsonNode visits = patient.path("visits");
                                    for (JsonNode visit : visits) {
                                        JsonNode antibioticPrescriptions = visit.path("antibiotic_prescriptions");
                                        for (JsonNode prescription : antibioticPrescriptions) {
                                            String atcCode = prescription.path("atc_code").asText();
                                            log.info("atc_code: {}", atcCode);

                                            String dddValue = fetchDddValue(atcCode);

                                            log.info("DDD Value for atc_code {}: {}", atcCode, dddValue);

                                            // compute the DDD:

                                            double averageMaintenanceDose = 0.0; // TBD

                                            if (dddValue != null && !dddValue.isEmpty()) {
                                                try {
                                                    double ddd = Double.parseDouble(dddValue);

                                                    if (ddd != 0) {
                                                        double computedDDD = averageMaintenanceDose / ddd;

                                                        log.info("Computed DDD for atc_code {}: {}", atcCode, computedDDD);
                                                    } else {
                                                        log.error("DDD Value is 0 for atc_code: {}", atcCode);
                                                    }
                                                } catch (NumberFormatException e) {
                                                    log.error("Invalid DDD Value for atc_code: {}", atcCode);
                                                }
                                            } else {
                                                log.error("No DDD Value found for atc_code: {}", atcCode);
                                            }

                                        }
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        },
                        Throwable::printStackTrace
                );
    }

    private String fetchDddValue(String atcCode) {
        try {
            String url = atcDddIndexUrl + atcCode + "&showdescription=no";

            Document doc = Jsoup.connect(url).get();

            Elements tables = doc.select("div#content table");

            if (tables.size() > 0) {
                Element table = tables.last();
                Elements rows = table.select("tr");

                if (rows.size() > 2) {
                    Element dddElement = rows.get(2).select("td").get(2);
                    String dddValue = dddElement.text().trim();

                    return dddValue;
                }
            }

            return "DDD value not found";
        } catch (IOException e) {
            e.printStackTrace();
            return "Error: Unable to fetch DDD value";
        }
    }

    public void getDailyAdmissions() {
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String patientId = "";
        //        String startDate = today.format(formatter);
        //        String endDate = today.format(formatter);

        String startDate = "2022-04-28";
        String endDate = "2023-09-28";

        httpClientService.fetchDailyAdmissions(patientId, startDate, endDate)
                .subscribe(
                        response -> {

                        },
                        Throwable::printStackTrace
                );
    }
}