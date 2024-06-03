package com.intellisoft.findams.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.intellisoft.findams.constants.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.json.JSONParser;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class EventProgramService {

    @Autowired
    HttpClientService httpClientService;

    @Autowired
    ObjectMapper objectMapper;

    public void fetchFromFunSoftBasedOnExecutionTime(Instant lastExecutionTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

        LocalDate lastExecutionDate = lastExecutionTime.atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate nextExecutionDate = lastExecutionDate.plusDays(1);
        LocalDate oneWeekLater = nextExecutionDate.plusDays(7);

        String startDate = nextExecutionDate.format(formatter);
        String endDate = oneWeekLater.format(formatter);

        fetchFromFunSoft(startDate, endDate);
    }

    public void fetchFromFunSoft(String startDate, String endDate) {

        String patientId = "";

        httpClientService.getPatientsAntibioticPrescriptions(patientId, startDate, endDate).subscribe(response -> {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(response);

                JsonNode patients = jsonNode.path("patients");
                for (JsonNode patient : patients) {
                    JsonNode visits = patient.path("visits");
                    for (JsonNode visit : visits) {
                        List<Map<String, Object>> eventsList = new ArrayList<>();

                        JsonNode antibioticPrescriptions = visit.path("antibiotic_prescriptions");
                        for (JsonNode prescription : antibioticPrescriptions) {

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
                            String combination = pharmaceuticalFormulation.path("combination").asText(); //AMC
                            String dosageForm = pharmaceuticalFormulation.path("dosage_form").asText(); //AMC
                            String department = prescription.path("department").asText(); //AMC
                            String numberOfPackagesDispensed = prescription.path("number_of_packages_being_dispensed").asText(); //AMC
                            String dateBeingDispensed = prescription.path("date_being_dispensed").asText(); //AMC
                            String lengthOfAdministration = prescription.path("length_of_administration").asText();
                            String occurredAt = visit.path("visit_date").asText();
                            String admissionDate = visit.path("admission_date").asText();
                            String dischargeDate = visit.path("discharge_date").asText();
                            String dispenseId = prescription.path("entry_id").asText();

                            Disposable disposable = httpClientService.getAmuMetaData().subscribe(programMetaData -> {
                                JSONObject jsonResponse = new JSONObject(programMetaData);
                                JSONArray programsArray = jsonResponse.getJSONArray("programs");

                                for (int i = 0; i < programsArray.length(); i++) {
                                    JSONObject programObject = programsArray.getJSONObject(i);
                                    String amuProgramId = programObject.getString("id");
                                    JSONArray programStageDataElementsArray = programObject.getJSONArray("programStages").getJSONObject(0).getJSONArray("programStageDataElements");

                                    // Fetch option sets
                                    Mono<Map<String, Map<String, String>>> optionSetsMono = httpClientService.fetchOptionSets();

                                    optionSetsMono.flatMap(optionSets -> {

                                        List<Map<String, Object>> eventSpecificDataValuesList = new ArrayList<>();

                                        for (int j = 0; j < programStageDataElementsArray.length(); j++) {
                                            JSONObject programStageDataElementObject = programStageDataElementsArray.getJSONObject(j);
                                            JSONObject dataElementObject = programStageDataElementObject.getJSONObject("dataElement");
                                            JSONObject amuProgramStageObject = programStageDataElementObject.getJSONObject("programStage");
                                            String amuProgramStageId = amuProgramStageObject.getString("id");
                                            String displayName = dataElementObject.getString("displayName");
                                            String id = dataElementObject.getString("id");


                                            Map<String, Object> ageData = new HashMap<>();
                                            if ("Age".equalsIgnoreCase(displayName)) {
                                                ageData = new HashMap<>();
                                                ageData.put("dataElement", id);
                                                ageData.put("value", age);
                                                eventSpecificDataValuesList.add(ageData);
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
                                                eventSpecificDataValuesList.add(genderData);
                                            }

                                            Map<String, Object> patientIdData = new HashMap<>();
                                            if ("Patient IP/OP No.".equalsIgnoreCase(displayName)) {
                                                patientIdData.put("dataElement", id);
                                                patientIdData.put("value", patient_id);
                                                eventSpecificDataValuesList.add(patientIdData);
                                            }

                                            //determine awareClassification:
                                            String awareClassification = extractAwareClassification(productName);

                                            Map<String, Object> categoryData = new HashMap<>();
                                            if ("Category (AMU)".equalsIgnoreCase(displayName)) {
                                                categoryData.put("dataElement", id);
                                                categoryData.put("value", awareClassification);
                                                eventSpecificDataValuesList.add(categoryData);
                                            }

                                            Map<String, Object> classData = new HashMap<>();
                                            if ("Class".equalsIgnoreCase(displayName)) {
                                                classData.put("dataElement", id);
                                                classData.put("value", class_);
                                                eventSpecificDataValuesList.add(classData);
                                            }

                                            Map<String, Object> patientDiagnosisData = new HashMap<>();
                                            if ("Patient diagnosis (Tentative and confirmatory dx)".equalsIgnoreCase(displayName)) {
                                                patientDiagnosisData.put("dataElement", id);
                                                patientDiagnosisData.put("value", confirmatoryDiagnosis);
                                                eventSpecificDataValuesList.add(patientDiagnosisData);
                                            }

                                            Map<String, Object> antiBioticDescription = new HashMap<>();
                                            if ("Antibiotics".equalsIgnoreCase(displayName)) {
                                                antiBioticDescription.put("dataElement", id);

                                                Map<String, String> optionSet = optionSets.get("Antibiotics");

                                                if (optionSet != null) {
                                                    String currentValue = productName;
                                                    String mappedOptionSetValue = optionSet.entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(currentValue)).map(Map.Entry::getKey).findFirst().orElse(currentValue);
                                                    antiBioticDescription.put("value", mappedOptionSetValue);
                                                } else {
                                                    antiBioticDescription.put("value", productName);
                                                }
                                                eventSpecificDataValuesList.add(antiBioticDescription);
                                            }

                                            Map<String, Object> payload = new HashMap<>();
                                            payload.put("occurredAt", LocalDate.now().toString());
                                            payload.put("completedAt", LocalDate.now().toString());
                                            payload.put("status", "COMPLETED");
                                            payload.put("notes", new ArrayList<>());
                                            payload.put("program", amuProgramId);
                                            payload.put("programStage", amuProgramStageId);
                                            payload.put("orgUnit", Constants.FIND_AMS_ORG_UNIT);
                                            payload.put("dataValues", eventSpecificDataValuesList);

                                            eventsList.add(payload);
                                        }

                                        Map<String, Object> finalPayload = new HashMap<>();
                                        finalPayload.put("events", eventsList);

                                        String finalPayloadJson = null;
                                        try {
                                            finalPayloadJson = objectMapper.writeValueAsString(finalPayload);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }

                                        JsonNode finalPayloadNode = null;
                                        try {
                                            finalPayloadNode = objectMapper.readTree(finalPayloadJson);

                                            JsonNode eventsNode = finalPayloadNode.path("events");

                                            if (eventsNode.isArray()) {
                                                Set<String> uniqueEventKeys = new HashSet<>();
                                                List<JsonNode> uniqueEvents = new ArrayList<>();

                                                for (JsonNode eventNode : eventsNode) {
                                                    String eventKey = eventNode.toString();

                                                    if (uniqueEventKeys.add(eventKey)) {
                                                        uniqueEvents.clear();
                                                        uniqueEvents.add(eventNode);
                                                    }
                                                }
                                                ((ArrayNode) eventsNode).removeAll();
                                                ((ArrayNode) eventsNode).addAll(uniqueEvents);
                                            }
                                            httpClientService.postAmuEventProgram(finalPayloadNode.toPrettyString()).subscribe(amuEventResponse -> {

                                            }, error -> {
                                                log.debug("Error occurred from DHIS2: {}", error.getMessage());
                                            });

                                            //processAmc
                                            processAmc(confirmatoryDiagnosis, productName, productId, strength, dosageForm, department, numberOfPackagesDispensed, dateBeingDispensed, occurredAt, combination);

                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }
                                        return Mono.empty();
                                    }).subscribe();
                                }
                            }, Throwable::printStackTrace);
                        }
                    }
                }
            } catch (IOException e) {
                log.error("IOException Error occurred while parsing JSON");
            }
        }, Throwable::printStackTrace);
    }


    private String extractAwareClassification(String productName) {
        String awareDataPath = Constants.TESTS_PATH + "amu_aware.json";

        try {
            String jsonContent = new String(Files.readAllBytes(Paths.get(awareDataPath)));

            JSONArray jsonArray = new JSONArray(jsonContent);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject entry = jsonArray.getJSONObject(i);
                if (productName.equals(entry.getString("name"))) {
                    return entry.getString("aware_classification");
                }
            }

            return "Unknown";
        } catch (IOException e) {
            e.printStackTrace();
            return "ErrorReadingJsonFile";
        }
    }

    private double fetchDDD(String productName) {

        String DDD_FILE_PATH = Constants.TESTS_PATH + "ddd.json";
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapper.readTree(new File(DDD_FILE_PATH));

            for (JsonNode productNode : jsonNode) {
                String name = productNode.get("Name").asText();
                if (productName.equalsIgnoreCase(name)) {
                    return productNode.get("DDD").asDouble();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    private void processAmc(String confirmatoryDiagnosis, String productName, String productId, String strength, String dosageForm, String department, String numberOfPackagesDispensed, String dateBeingDispensed, String occurredAt, String combination) {

        String patientId = "";

        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate dayAfterTomorrow = tomorrow.plusDays(1);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String startDate = tomorrow.format(formatter);
        String endDate = dayAfterTomorrow.format(formatter);

        httpClientService.fetchDailyAdmissions(patientId, startDate, endDate).subscribe(response -> {


            Disposable disposable = httpClientService.getAmcMetaData().subscribe(programMetaData -> {
                JSONObject jsonResponse = new JSONObject(programMetaData);
                JSONArray programsArray = jsonResponse.getJSONArray("programs");

                Mono<Map<String, Map<String, String>>> optionSetsMono = httpClientService.fetchOptionSets();

                optionSetsMono.flatMap(optionSets -> {
                    List<Map<String, Object>> eventsList = new ArrayList<>();

                    for (int i = 0; i < programsArray.length(); i++) {
                        JSONObject programObject = programsArray.getJSONObject(i);
                        String amcProgramId = programObject.getString("id");
                        JSONArray programStageDataElementsArray = programObject.getJSONArray("programStages").getJSONObject(0).getJSONArray("programStageDataElements");

                        List<Map<String, Object>> dataValuesList = new ArrayList<>();

                        for (int j = 0; j < programStageDataElementsArray.length(); j++) {
                            JSONObject programStageDataElementObject = programStageDataElementsArray.getJSONObject(j);
                            JSONObject dataElementObject = programStageDataElementObject.getJSONObject("dataElement");
                            JSONObject amuProgramStageObject = programStageDataElementObject.getJSONObject("programStage");
                            String amuProgramStageId = amuProgramStageObject.getString("id");
                            String displayName = dataElementObject.getString("displayName");
                            String amcId = dataElementObject.getString("id");

                            JsonNode admissionsData = null;
                            try {
                                admissionsData = objectMapper.readTree(response);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                            int totalAdmissions = admissionsData.path("daily_admissions").get(0).path("total_admissions").asInt();

                            // DDD computation:
                            double dddValueResponse = fetchDDD(productName);

                            Double dailyDefinedDosage = null;

                            try {

                                double medicalStrength;

                                if (strength.equals("-") || strength.equals("") || fractionChecker.isFraction(strength)) {
                                    medicalStrength = 0;
                                } else {
                                    medicalStrength = Double.parseDouble(strength);
                                }
                                // Converting strength to g
                                dailyDefinedDosage = ((medicalStrength / 1000) / dddValueResponse);
                            } catch (NumberFormatException e) {
                                log.error("Error parsing strength as a number");
                            }

                            // Determine aware:
                            String awareClassification = extractAwareClassification(productName);

                            Map<String, Object> amcCategoryData = new HashMap<>();
                            if ("Category (AMC)".equalsIgnoreCase(displayName)) {
                                amcCategoryData.put("dataElement", amcId);
                                amcCategoryData.put("value", awareClassification);
                                dataValuesList.add(amcCategoryData);
                            }

                            Map<String, Object> numberOfPackagesDispensedData = new HashMap<>();
                            if ("Number of packages being dispensed".equals(displayName)) {
                                numberOfPackagesDispensedData.put("dataElement", amcId);
                                numberOfPackagesDispensedData.put("value", numberOfPackagesDispensed);
                                dataValuesList.add(numberOfPackagesDispensedData);
                            }

                            Map<String, Object> dateBeingDispensedData = new HashMap<>();
                            if ("Date being dispensed".equals(displayName)) {
                                dateBeingDispensedData.put("dataElement", amcId);
                                dateBeingDispensedData.put("value", dateBeingDispensed);
                                dataValuesList.add(dateBeingDispensedData);
                            }

                            Map<String, Object> combinationData = new HashMap<>();
                            if ("Combination".equals((displayName))) {
                                combinationData.put("dataElement", amcId);
                                combinationData.put("value", combination);
                                dataValuesList.add(combinationData);
                            }

                            if ("Daily defined dose".equals((displayName))) {
                                Map<String, Object> dosageData = new HashMap<>();
                                dosageData.put("dataElement", amcId);
                                if (dailyDefinedDosage != null && !Double.isInfinite(dailyDefinedDosage)) {
                                    dosageData.put("value", dailyDefinedDosage.intValue());
                                } else {
                                    dosageData.put("value", 0.0);
                                }
                                dataValuesList.add(dosageData);
                            }

                            if ("Dosage form".equals(displayName)) {
                                Map<String, Object> dosageFormData = new HashMap<>();
                                dosageFormData.put("dataElement", amcId);
                                dosageFormData.put("value", dosageForm);
                                dataValuesList.add(dosageFormData);
                            }


                            Map<String, Object> departmentData = new HashMap<>();
                            if ("Department".equals(displayName)) {
                                departmentData.put("dataElement", amcId);

                                Map<String, String> optionSet = optionSets.get("Department");

                                if (optionSet != null) {
                                    String currentValue = department;
                                    String mappedOptionSetValue = optionSet.entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(currentValue)).map(Map.Entry::getKey).findFirst().orElse("UKN"); // Default to "Unknown" if not found in options

                                    departmentData.put("value", mappedOptionSetValue);
                                } else {
                                    departmentData.put("value", "UKN"); // Unknown Department
                                }


                                dataValuesList.add(departmentData);
                            }

                            if ("Unique product identifier (code)".equals(displayName)) {
                                Map<String, Object> productIdData = new HashMap<>();
                                productIdData.put("dataElement", amcId);
                                productIdData.put("value", productId);
                                dataValuesList.add(productIdData);
                            }

                            if ("Diagnosis".equals(displayName)) {
                                Map<String, Object> diagnosisData = new HashMap<>();
                                diagnosisData.put("dataElement", amcId);
                                diagnosisData.put("value", confirmatoryDiagnosis);
                                dataValuesList.add(diagnosisData);
                            }

                            // determine the top-infectious-condition categorization based on the diagnosis
                            String topInfectiousCondition = getTopInfectiousCondition(confirmatoryDiagnosis);

                            Map<String, Object> topTenData = new HashMap<>();
                            if ("Top infectious conditions".equals(displayName)) {
                                topTenData.put("dataElement", amcId);

                                Map<String, String> optionSet = optionSets.get("Top infectious conditions");

                                if (optionSet != null) {
                                    String currentValue = topInfectiousCondition;
                                    String mappedOptionSetValue = optionSet.entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(currentValue)).map(Map.Entry::getKey).findFirst().orElse("UKN");
                                    topTenData.put("value", mappedOptionSetValue);
                                } else {
                                    topTenData.put("value", "UKN"); //Unknown
                                }

                                dataValuesList.add(topTenData);
                            }

                            if ("Daily number of admissions".equals(displayName)) {
                                Map<String, Object> totalAdmissionsData = new HashMap<>();
                                totalAdmissionsData.put("dataElement", amcId);
                                totalAdmissionsData.put("value", totalAdmissions);
                                dataValuesList.add(totalAdmissionsData);
                            }

                            Map<String, Object> productNameMap = new HashMap<>();
                            if ("Product name".equalsIgnoreCase(displayName)) {
                                productNameMap.put("dataElement", amcId);

                                Map<String, String> optionSet = optionSets.get("Antibiotics");

                                if (optionSet != null) {
                                    String currentValue = productName;
                                    String mappedOptionSetValue = optionSet.entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(currentValue)).map(Map.Entry::getKey).findFirst().orElse(currentValue);
                                    productNameMap.put("value", mappedOptionSetValue);
                                } else {
                                    productNameMap.put("value", productName);
                                }
                                dataValuesList.add(productNameMap);
                            }

                        }

                        Map<String, Object> payload = new HashMap<>();
                        payload.put("occurredAt", LocalDate.now().toString());
                        payload.put("status", "COMPLETED");
                        payload.put("notes", new ArrayList<>());
                        payload.put("completedAt", LocalDate.now().toString());
                        payload.put("program", amcProgramId);
                        payload.put("programStage", Constants.AMC_PROGRAM_STAGE_UNIT);
                        payload.put("orgUnit", Constants.FIND_AMS_ORG_UNIT);
                        payload.put("dataValues", dataValuesList);
                        eventsList.add(payload);
                    }

                    Map<String, Object> finalPayload = new HashMap<>();
                    finalPayload.put("events", eventsList);

                    String finalPayloadJson = null;

                    try {
                        finalPayloadJson = objectMapper.writeValueAsString(finalPayload);
                        JsonNode finalPayloadNode = objectMapper.readTree(finalPayloadJson);

                        JsonNode eventsNode = finalPayloadNode.path("events");

                        if (eventsNode.isArray()) {
                            Set<String> uniqueEventKeys = new HashSet<>();
                            List<JsonNode> uniqueEvents = new ArrayList<>();

                            for (JsonNode eventNode : eventsNode) {
                                String eventKey = eventNode.toString();

                                if (uniqueEventKeys.add(eventKey)) {
                                    uniqueEvents.clear();
                                    uniqueEvents.add(eventNode);
                                }
                            }

                            ((ArrayNode) eventsNode).removeAll();
                            ((ArrayNode) eventsNode).addAll(uniqueEvents);
                        }

                        httpClientService.postAmcEventProgram(finalPayloadNode.toPrettyString()).doOnError(error -> {
                            log.debug("Error occurred from DHIS2: {}", error.getMessage());
                        }).subscribe(AmcDhisResponse -> {
                        });

                    } catch (JsonProcessingException e) {
                        log.error("Error occurred while processing JSON");
                    }
                    return Mono.empty();
                }).subscribe();

            }, Throwable::printStackTrace);
        });

    }

    private String getTopInfectiousCondition(String confirmatoryDiagnosis) {
        String topTenPath = Constants.TESTS_PATH + "topten.json";

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            List<Map<String, String>> jsonList = objectMapper.readValue(new File(topTenPath), new TypeReference<>() {
            });

            for (Map<String, String> map : jsonList) {
                String icdSubClassification = map.get("icd_sub_classification");
                if (confirmatoryDiagnosis.equals(icdSubClassification)) {
                    return map.get("top_infectious_condition");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }


    public static class fractionChecker {
        public static boolean isFraction(String input) {
            if (input == null || input.isEmpty()) {
                return false;
            }

            String[] parts = input.split("/");
            if (parts.length != 2) {
                return false;
            }

            try {
                int numerator = Integer.parseInt(parts[0].trim());
                int denominator = Integer.parseInt(parts[1].trim());

                return denominator != 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }


}