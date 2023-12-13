package com.intellisoft.findams.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellisoft.findams.constants.Constants;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EventProgramService {

    @Autowired
    HttpClientService httpClientService;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${ams.funsoft.atc-ddd-index-url}")
    private String atcDddIndexUrl;

    private Disposable fetchDddValue(String medicationName) {
        return httpClientService.fetchDddValue(medicationName).subscribe();
    }


    public void fetchFromFunSoft() {

        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String patientId = "";
        //        String startDate = today.format(formatter);
        //        String endDate = today.format(formatter);

        String startDate = "2022-04-28";
        String endDate = "2023-09-28";

        httpClientService.getPatientsAntibioticPrescriptions(patientId, startDate, endDate).subscribe(response -> {

            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(response);

                JsonNode patients = jsonNode.path("patients");
                for (JsonNode patient : patients) {
                    JsonNode visits = patient.path("visits");
                    for (JsonNode visit : visits) {
                        JsonNode antibioticPrescriptions = visit.path("antibiotic_prescriptions");
                        for (JsonNode prescription : antibioticPrescriptions) {

                            List<Map<String, Object>> dataValuesList = new ArrayList<>();

                            String age = visit.path("age").asText();
                            String gender = patient.path("gender").asText();
                            String patient_id = patient.path("patient_id").asText();
                            String category = prescription.path("category").asText();
                            String class_ = prescription.path("class").asText();
                            JsonNode patientDiagnosis = visit.path("patient_diagnosis");
                            String confirmatoryDiagnosis = patientDiagnosis.path("confirmatory_diagnosis").asText();
                            String tentativeDiagnosis = patientDiagnosis.path("tentative_diagnosis").asText();
                            JsonNode pharmaceuticalFormulation = prescription.path("pharmaceutical_formulation");
                            String productName = prescription.path("product_name").asText(); //AMC/AMU
                            String productId = prescription.path("product_id").asText(); //AMC
                            String strength = pharmaceuticalFormulation.path("strength").asText(); //AMC
                            String dosageForm = pharmaceuticalFormulation.path("dosage_form").asText(); //AMC
                            String department = prescription.path("department").asText(); //AMC
                            String numberOfPackagesDispensed = prescription.path("number_of_packages_being_dispensed").asText(); //AMC
                            String dateBeingDispensed = prescription.path("date_being_dispensed").asText(); //AMC
                            String occurredAt = visit.path("visit_date").asText();


                            Disposable disposable = httpClientService.getAmuMetaData().subscribe(programMetaData -> {
                                JSONObject jsonResponse = new JSONObject(programMetaData);
                                JSONArray programsArray = jsonResponse.getJSONArray("programs");
                                Map<String, Object> finalPayload = new HashMap<>();
                                List<Map<String, Object>> eventsList = new ArrayList<>();

                                for (int i = 0; i < programsArray.length(); i++) {
                                    JSONObject programObject = programsArray.getJSONObject(i);
                                    String amuProgramId = programObject.getString("id");
                                    JSONArray programStageDataElementsArray = programObject.getJSONArray("programStages").getJSONObject(0).getJSONArray("programStageDataElements");

                                    // Fetch option sets
                                    Mono<Map<String, Map<String, String>>> optionSetsMono = httpClientService.fetchOptionSets();

                                    optionSetsMono.flatMap(optionSets -> {
                                        for (int j = 0; j < programStageDataElementsArray.length(); j++) {
                                            JSONObject programStageDataElementObject = programStageDataElementsArray.getJSONObject(j);
                                            JSONObject dataElementObject = programStageDataElementObject.getJSONObject("dataElement");
                                            JSONObject amuProgramStageObject = programStageDataElementObject.getJSONObject("programStage");
                                            String amuProgramStageId = amuProgramStageObject.getString("id");
                                            String displayName = dataElementObject.getString("displayName");
                                            String id = dataElementObject.getString("id");

                                            // Add your logic here to process data elements

                                            Map<String, Object> payload = new HashMap<>();
                                            payload.put("occurredAt", visit.path("visit_date").asText());
                                            payload.put("notes", new ArrayList<>());
                                            payload.put("program", amuProgramId);
                                            payload.put("programStage", amuProgramStageId);
                                            payload.put("orgUnit", Constants.FIND_AMS_ORG_UNIT);

                                            Map<String, Object> ageData = new HashMap<>();
                                            if ("Age".equalsIgnoreCase(displayName)) {
                                                ageData = new HashMap<>();
                                                ageData.put("dataElement", id);
                                                ageData.put("value", age);
                                                dataValuesList.add(ageData);
                                            }

                                            Map<String, Object> genderData = new HashMap<>();
                                            if ("Gender".equalsIgnoreCase(displayName)) {
                                                genderData.put("dataElement", id);

                                                Map<String, String> optionSet = optionSets.get("Gender");

                                                if (optionSet != null) {
                                                    String currentValue = gender;

                                                    String mappedOptionSetValue = optionSet.entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(currentValue)).map(Map.Entry::getKey).findFirst().orElse(currentValue);

                                                    genderData.put("value", mappedOptionSetValue);
                                                } else {
                                                    genderData.put("value", gender);
                                                }
                                                dataValuesList.add(genderData);
                                            }

                                            Map<String, Object> patientIdData = new HashMap<>();
                                            if ("Patient IP/OP No.".equalsIgnoreCase(displayName)) {
                                                patientIdData.put("dataElement", id);
                                                patientIdData.put("value", patient_id);
                                                dataValuesList.add(patientIdData);
                                            }

                                            Map<String, Object> categoryData = new HashMap<>();
                                            if ("Category".equalsIgnoreCase(displayName)) {
                                                categoryData.put("dataElement", id);
                                                categoryData.put("value", category);
                                                dataValuesList.add(categoryData);
                                            }

                                            Map<String, Object> classData = new HashMap<>();
                                            if ("Class".equalsIgnoreCase(displayName)) {
                                                classData.put("dataElement", id);
                                                classData.put("value", class_);
                                                dataValuesList.add(classData);
                                            }

                                            Map<String, Object> patientDiagnosisData = new HashMap<>();
                                            if ("Patient diagnosis (Tentative and confirmatory dx)".equalsIgnoreCase(displayName)) {
                                                patientDiagnosisData.put("dataElement", id);
                                                patientDiagnosisData.put("value", confirmatoryDiagnosis);
                                                dataValuesList.add(patientDiagnosisData);
                                            }

                                            Map<String, Object> antiBioticDescription = new HashMap<>();
                                            if ("Antibiotic".equalsIgnoreCase(displayName)) {
                                                antiBioticDescription.put("dataElement", id);

                                                Map<String, String> optionSet = optionSets.get("Drugs");

                                                if (optionSet != null) {
                                                    String currentValue = productName;
                                                    String mappedOptionSetValue = optionSet.entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(currentValue)).map(Map.Entry::getKey).findFirst().orElse(currentValue);
                                                    antiBioticDescription.put("value", mappedOptionSetValue);
                                                } else {
                                                    antiBioticDescription.put("value", productName);
                                                }
                                                dataValuesList.add(antiBioticDescription);
                                            }

                                            payload.put("dataValues", dataValuesList);
                                            eventsList.add(payload);
                                            finalPayload.put("events", eventsList);

                                            String finalPayloadJson = null;
                                            try {
                                                finalPayloadJson = objectMapper.writeValueAsString(finalPayload);
                                                log.info("AMC finalPayloadJson: {}", finalPayloadJson);
//                                                httpClientService.postAmuEventProgram(finalPayloadJson).doOnError(error -> {
//                                                    log.error("Error occurred from DHIS2: {}", error.getMessage());
//                                                }).subscribe(AmuDhisResponse -> {
//                                                    log.info("DHIS2 response FOR AMU: {}", AmuDhisResponse);

                                                    //processAmc
                                                    processAmc(tentativeDiagnosis, productName, productId, strength, dosageForm, department, numberOfPackagesDispensed, dateBeingDispensed, occurredAt);
//                                                });
                                            } catch (JsonProcessingException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                        return Mono.empty();
                                    }).subscribe();
                                }
                            }, Throwable::printStackTrace);

                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, Throwable::printStackTrace);

    }

    private void processAmc(String tentativeDiagnosis, String productName, String productId, String strength, String dosageForm, String department, String numberOfPackagesDispensed, String dateBeingDispensed, String occurredAt) {

        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String patientId = "";
        //        String startDate = today.format(formatter);
        //        String endDate = today.format(formatter);

        String startDate = "2022-04-28";
        String endDate = "2023-09-28";

        httpClientService.fetchDailyAdmissions(patientId, startDate, endDate).subscribe(fetchedAdmissionsResponse -> {


            Disposable disposable = httpClientService.getAmcMetaData().subscribe(programMetaData -> {
                JSONObject jsonResponse = new JSONObject(programMetaData);
                JSONArray programsArray = jsonResponse.getJSONArray("programs");
                Map<String, Object> finalPayload = new HashMap<>();
                List<Map<String, Object>> eventsList = new ArrayList<>();

                for (int i = 0; i < programsArray.length(); i++) {
                    JSONObject programObject = programsArray.getJSONObject(i);
                    String amuProgramId = programObject.getString("id");
                    JSONArray programStageDataElementsArray = programObject.getJSONArray("programStages").getJSONObject(0).getJSONArray("programStageDataElements");

                    // Fetch option sets
                    Mono<Map<String, Map<String, String>>> optionSetsMono = httpClientService.fetchOptionSets();

                    List<Map<String, Object>> amcDataValuesList = new ArrayList<>();

                    optionSetsMono.flatMap(optionSets -> {
                        for (int j = 0; j < programStageDataElementsArray.length(); j++) {
                            JSONObject programStageDataElementObject = programStageDataElementsArray.getJSONObject(j);
                            JSONObject amcDataElementObject = programStageDataElementObject.getJSONObject("dataElement");
                            JSONObject amuProgramStageObject = programStageDataElementObject.getJSONObject("programStage");
                            String amuProgramStageId = amuProgramStageObject.getString("id");
                            String amcDisplayName = amcDataElementObject.getString("displayName");
                            String amcId = amcDataElementObject.getString("id");

                            Map<String, Object> payload = new HashMap<>();
                            payload.put("occurredAt", occurredAt);
                            payload.put("notes", new ArrayList<>());
                            payload.put("program", amuProgramId);
                            payload.put("programStage", amuProgramStageId);
                            payload.put("orgUnit", Constants.FIND_AMS_ORG_UNIT);

                            Map<String, Object> productIdData = new HashMap<>();
                            if ("Unique product identifier (code)".equals(amcDisplayName)) {
                                productIdData.put("dataElement", amcId);
                                productIdData.put("value", productId);
                                amcDataValuesList.add(productIdData);
                            }

                            Map<String, Object> strengthData = new HashMap<>();
                            if ("Strength".equals(amcDisplayName)) {
                                strengthData.put("dataElement", amcId);
                                strengthData.put("value", strength);
                                amcDataValuesList.add(strengthData);
                            }

                            Map<String, Object> dosageFormData = new HashMap<>();
                            if ("Dosage form".equals(amcDisplayName)) {
                                dosageFormData.put("dataElement", amcId);
                                dosageFormData.put("value", dosageForm);
                                amcDataValuesList.add(dosageFormData);
                            }


                            Map<String, Object> departmentData = new HashMap<>();
                            if ("Department".equalsIgnoreCase(amcDisplayName)) {
                                departmentData.put("dataElement", amcId);

                                Map<String, String> optionSet = optionSets.get("Department");
                                if (optionSet != null) {
                                    String currentValue = department;

                                    String mappedOptionSetValue = optionSet.entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(currentValue)).map(Map.Entry::getKey).findFirst().orElse(currentValue);

                                    departmentData.put("value", mappedOptionSetValue);
                                } else {
                                    departmentData.put("value", department);
                                }
                                amcDataValuesList.add(departmentData);
                            }


                            Map<String, Object> numberOfPackagesDispensedData = new HashMap<>();
                            if ("Number of packages being dispensed".equals(amcDisplayName)) {
                                numberOfPackagesDispensedData.put("dataElement", amcId);
                                numberOfPackagesDispensedData.put("value", numberOfPackagesDispensed);
                                amcDataValuesList.add(numberOfPackagesDispensedData);
                            }

                            if ("Date being dispensed".equals(amcDisplayName)) {
                                Map<String, Object> dateBeingDispensedData = new HashMap<>();
                                dateBeingDispensedData.put("dataElement", amcId);
                                dateBeingDispensedData.put("value", dateBeingDispensed);
                                amcDataValuesList.add(dateBeingDispensedData);
                            }

                            payload.put("dataValues", amcDataValuesList);
                            eventsList.add(payload);
                            finalPayload.put("events", eventsList);

                            String finalPayloadJson = null;
                            try {
                                finalPayloadJson = objectMapper.writeValueAsString(finalPayload);
//                                httpClientService.postAmcEventProgram(finalPayloadJson).doOnError(error -> {
//                                    log.error("Error occurred from DHIS2: {}", error.getMessage());
//                                }).subscribe(AmcDhisResponse -> {
//                                    log.info("DHIS2 response FOR AMC: {}", AmcDhisResponse);
//                                });
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        return Mono.empty();
                    }).subscribe();
                }
            }, Throwable::printStackTrace);
        });
    }

}