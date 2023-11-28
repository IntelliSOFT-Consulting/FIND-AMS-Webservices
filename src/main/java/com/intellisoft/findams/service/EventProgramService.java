package com.intellisoft.findams.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellisoft.findams.constants.Constants;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileReader;
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

    private static Double fetchDddValue(String medicationName) {
        try (CSVReader reader = new CSVReaderBuilder(new FileReader(Constants.ANTIBIOTICS_FOLDER_PATH)).withSkipLines(1).build()) {
            List<String[]> rows = reader.readAll();

            for (String[] row : rows) {
                String name = row[1];
                String dddValue = row[2];

                if (medicationName.equalsIgnoreCase(name.trim())) {
                    return Double.valueOf(dddValue.trim());
                }
            }
        } catch (IOException e) {
            // Handle IOException
            e.printStackTrace();
        } catch (CsvException e) {
            // Log the exception and return 0.0
            System.err.println("Error reading CSV file: " + e.getMessage());
            return 0.0;
        }

        // Return 0.0 if medicationName is not found or there is an issue
        return 0.0;
    }


    public void fetchAMUData() {

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
                System.out.println(patients);
                for (JsonNode patient : patients) {
                    JsonNode visits = patient.path("visits");
                    for (JsonNode visit : visits) {
                        JsonNode antibioticPrescriptions = visit.path("antibiotic_prescriptions");
                        for (JsonNode prescription : antibioticPrescriptions) {

                            JsonNode pharmaceuticFormulation = prescription.path("pharmaceutical_formulation");

                            // Access the properties of pharmaceutical_formulation
                            String pharmaceuticStrength = pharmaceuticFormulation.path("strength").asText();

                            // compute the DDD:
                            if (pharmaceuticStrength.equals("")) {
                                double averageMaintenanceDose = 0.0;

                            } else {

                                double averageMaintenanceDose = Double.parseDouble(pharmaceuticStrength);

                                log.info("averageMaintenanceDose: {}", averageMaintenanceDose);

                                String medicationName = visit.path("product_name").asText();
//                                String dddValue = String.valueOf(fetchDddValue(medicationName));

                                String dddValue = "7";

                                if (!dddValue.isEmpty()) {
                                    try {
                                        double ddd = Double.parseDouble(dddValue);

                                        if (ddd != 0) {
                                            double computedDDD = (averageMaintenanceDose / 1000) / ddd;

                                            log.info("Computed DDD for product_name {}: {}", medicationName, computedDDD);

                                            Map<String, Object> payload = new HashMap<>();
                                            payload.put("occurredAt", visit.path("visit_date").asText());
                                            payload.put("notes", new ArrayList<>());
                                            payload.put("program", Constants.PROGRAM);
                                            payload.put("programStage", Constants.PROGRAM_STAGE_ID);
                                            payload.put("orgUnit", Constants.ORG_UNIT);

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

                                            Map<String, Object> ageData = new HashMap<>();
                                            ageData.put("displayName", "");
                                            ageData.put("dataElement", Constants.AGE);
                                            ageData.put("value", age);

                                            Map<String, Object> genderData = new HashMap<>();
                                            ageData.put("displayName", "");
                                            genderData.put("dataElement", Constants.GENDER);
                                            genderData.put("value", gender);

                                            Map<String, Object> patientIdData = new HashMap<>();
                                            ageData.put("displayName", "");
                                            patientIdData.put("dataElement", Constants.PATIENT_IP_OP_NO);
                                            patientIdData.put("value", patient_id);

                                            Map<String, Object> categoryData = new HashMap<>();
                                            ageData.put("displayName", "");
                                            categoryData.put("dataElement", Constants.CATEGORY);
                                            categoryData.put("value", category);

                                            Map<String, Object> classData = new HashMap<>();
                                            ageData.put("displayName", "");
                                            classData.put("dataElement", Constants.CLASS);
                                            classData.put("value", class_);

                                            Map<String, Object> patientDiagnosisData = new HashMap<>();
                                            ageData.put("displayName", "");
                                            patientDiagnosisData.put("dataElement", Constants.PATIENT_DIAGNOSIS_TENTATIVE_CONFIRMATORY_DX);
                                            patientDiagnosisData.put("value", confirmatoryDiagnosis);

                                            Map<String, Object> antiBioticDescription = new HashMap<>();
                                            antiBioticDescription.put("displayName", "Drugs"); //has options
                                            antiBioticDescription.put("dataElement", Constants.ANTIBIOTIC_PRESCRIPTION);
                                            antiBioticDescription.put("value", productName);

                                            Map<String, Object> amcAntibioticData = new HashMap<>();
                                            amcAntibioticData.put("dataElement", Constants.PRODUCT_NAME);
                                            amcAntibioticData.put("value", productName);

                                            dataValuesList.add(ageData);
                                            dataValuesList.add(genderData);
                                            dataValuesList.add(patientIdData);
                                            dataValuesList.add(categoryData);
                                            dataValuesList.add(classData);
                                            dataValuesList.add(patientDiagnosisData);
                                            dataValuesList.add(antiBioticDescription);

                                            payload.put("dataValues", dataValuesList);

                                            List<Map<String, Object>> eventsList = new ArrayList<>();
                                            eventsList.add(payload);

                                            Map<String, Object> finalPayload = new HashMap<>();
                                            finalPayload.put("events", eventsList);
                                            String finalPayloadJson = objectMapper.writeValueAsString(finalPayload);

                                            System.out.println("amUPayloadToPost" + finalPayloadJson);

                                             httpClientService.postAmuEventProgram(finalPayloadJson).doOnError(error -> {//
                                                log.error("Error occurred from DHIS2{}", error.getMessage());
                                            }).subscribe(AmuDhisResponse -> {

                                                log.info("DHIS2 response FOR AMU: {}", AmuDhisResponse);

                                                // proceed to AMC
                                                httpClientService.fetchDailyAdmissions(patientId, startDate, endDate).subscribe(fetchedAdmissionsResponse -> {

                                                    JsonNode admissionsData = null;
                                                    try {
                                                        admissionsData = objectMapper.readTree(fetchedAdmissionsResponse);
                                                    } catch (JsonProcessingException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                    JsonNode departmentAdmissions = admissionsData.path("daily_admissions").get(0).path("department_admissions");

                                                    String amcDepartment = department;

                                                    int totalAdmissions = 0;

                                                    if (departmentAdmissions.has(amcDepartment)) {
                                                        totalAdmissions = departmentAdmissions.get(amcDepartment).asInt();
                                                    }

                                                    // construct AMC payload
                                                    Map<String, Object> amcPayload = new HashMap<>();

                                                    amcPayload.put("occurredAt", visit.path("visit_date").asText());
                                                    amcPayload.put("notes", new ArrayList<>());
                                                    amcPayload.put("program", Constants.AMC_PROGRAM);
                                                    amcPayload.put("programStage", Constants.AMC_PROGRAM_STAGE_ID);
                                                    amcPayload.put("orgUnit", Constants.ORG_UNIT);

                                                    List<Map<String, Object>> amcDataValuesList = new ArrayList<>();


                                                    Map<String, Object> productIdData = new HashMap<>();
                                                    productIdData.put("dataElement", Constants.UNIQUE_PRODUCT_IDENTIFIER);
                                                    productIdData.put("value", productId);

                                                    Map<String, Object> strengthData = new HashMap<>();
                                                    strengthData.put("dataElement", Constants.STRENGTH);
                                                    strengthData.put("value", strength);

                                                    Map<String, Object> dosageFormData = new HashMap<>();
                                                    dosageFormData.put("dataElement", Constants.DOSAGE_FORM);
                                                    dosageFormData.put("value", dosageForm);

                                                    Map<String, Object> departmentData = new HashMap<>();
                                                    departmentData.put("dataElement", Constants.DEPARTMENT);
                                                    departmentData.put("value", department);

                                                    Map<String, Object> numberOfPackagesDispensedData = new HashMap<>();
                                                    numberOfPackagesDispensedData.put("dataElement", Constants.NUMBER_OF_PACKAGES_BEING_DISPENSED);
                                                    numberOfPackagesDispensedData.put("value", numberOfPackagesDispensed);


                                                    Map<String, Object> dateBeingDispensedData = new HashMap<>();
                                                    dateBeingDispensedData.put("dataElement", Constants.DATE_BEING_DISPENSED);
                                                    dateBeingDispensedData.put("value", dateBeingDispensed);


                                                    Map<String, Object> totalAdmissionsData = new HashMap<>();
                                                    totalAdmissionsData.put("dataElement", Constants.DAILY_NUMBER_OF_ADMISSIONS);
                                                    totalAdmissionsData.put("value", totalAdmissions);

                                                    Map<String, Object> dailyDosageData = new HashMap<>();
                                                    dailyDosageData.put("dataElement", Constants.DAILY_DEFINED_DOSAGE);
                                                    dailyDosageData.put("value", computedDDD);

                                                    amcDataValuesList.add(productIdData);
                                                    amcDataValuesList.add(strengthData);
                                                    amcDataValuesList.add(dosageFormData);
                                                    amcDataValuesList.add(departmentData);
                                                    amcDataValuesList.add(numberOfPackagesDispensedData);
                                                    amcDataValuesList.add(dateBeingDispensedData);
                                                    amcDataValuesList.add(totalAdmissionsData);
                                                    amcDataValuesList.add(amcAntibioticData);
                                                    amcDataValuesList.add(dailyDosageData);

                                                    amcPayload.put("dataValues", amcDataValuesList);

                                                    List<Map<String, Object>> amcEventsList = new ArrayList<>();
                                                    amcEventsList.add(amcPayload);

                                                    Map<String, Object> amcEventPayload = new HashMap<>();
                                                    amcEventPayload.put("events", amcEventsList);

                                                    try {
                                                        String amcPayloadToPost = objectMapper.writeValueAsString(amcEventPayload);

                                                        System.out.println("amcPayloadToPost" + amcPayloadToPost);

                                                        httpClientService.postAmcEventProgram(amcPayloadToPost).doOnError(error -> {

                                                            log.error("Error occurred from DHIS2{}", error.getMessage());

                                                        }).subscribe(AmcDhisResponse -> {
                                                            log.info("DHIS2 response FOR AMC: {}", AmcDhisResponse);
                                                        });
                                                    } catch (JsonProcessingException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                }, Throwable::printStackTrace);
                                            });
                                        } else {
                                            log.error("DDD Value is 0 for product_name: {}", medicationName);
                                        }
                                    } catch (NumberFormatException e) {
                                        log.error("Invalid DDD Value for product_name: {}", medicationName);
                                    }
                                } else {
                                    log.error("No DDD Value found for product_name: {}", medicationName);
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, Throwable::printStackTrace);

    }

    private String mapToOption(String originalValue, Map<String, String> optionSetsMap) {
        return optionSetsMap.get(originalValue);
    }

}