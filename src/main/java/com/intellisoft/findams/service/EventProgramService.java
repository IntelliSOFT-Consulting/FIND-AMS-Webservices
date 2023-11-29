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


                            Disposable disposable = httpClientService.getAmuMetaData().subscribe(

                                    programMetaData -> {

                                        JSONObject jsonResponse = new JSONObject(programMetaData);

                                        JSONArray programsArray = jsonResponse.getJSONArray("programs");

                                        Map<String, Object> finalPayload = new HashMap<>();

                                        List<Map<String, Object>> eventsList = new ArrayList<>();

                                        for (int i = 0; i < programsArray.length(); i++) {

                                            JSONObject programObject = programsArray.getJSONObject(i);

                                            // Get program ID
                                            String amuProgramId = programObject.getString("id");

                                            JSONArray programStageDataElementsArray = programObject.getJSONArray("programStages").getJSONObject(0).getJSONArray("programStageDataElements");

                                            Map<String, Object> payload = null;
                                            for (int j = 0; j < programStageDataElementsArray.length(); j++) {

                                                payload = new HashMap<>();

                                                JSONObject programStageDataElementObject = programStageDataElementsArray.getJSONObject(j);

                                                JSONObject dataElementObject = programStageDataElementObject.getJSONObject("dataElement");

                                                JSONObject amuProgramStageObject = programStageDataElementObject.getJSONObject("programStage");
                                                String amuProgramStageId = amuProgramStageObject.getString("id");


                                                String displayName = dataElementObject.getString("displayName");
                                                String id = dataElementObject.getString("id");


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
                                                    genderData.put("value", gender);
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
                                                    antiBioticDescription.put("value", productName);
                                                    dataValuesList.add(antiBioticDescription);
                                                }
                                            }
//                                    if ("Age".equalsIgnoreCase(displayName)) {
//                                        Map<String, Object> amcAntibioticData = new HashMap<>();
//                                        amcAntibioticData.put("dataElement", Constants.PRODUCT_NAME);
//                                        amcAntibioticData.put("value", productName);
//                                    }

                                            payload.put("dataValues", dataValuesList);
                                            eventsList.add(payload);
                                            finalPayload.put("events", eventsList);
                                            String finalPayloadJson = null;
                                            try {
                                                finalPayloadJson = objectMapper.writeValueAsString(finalPayload);
                                            } catch (JsonProcessingException e) {
                                                throw new RuntimeException(e);
                                            }
                                            System.out.println("finalPayloadJson" + finalPayloadJson);

                                            httpClientService.postAmuEventProgram(finalPayloadJson).doOnError(error -> {
                                                log.error("Error occurred from DHIS2{}", error.getMessage());
                                            }).subscribe(AmuDhisResponse -> {

                                                log.info("DHIS2 response FOR AMU: {}", AmuDhisResponse);

                                                // proceed to AMC
                                                httpClientService.fetchDailyAdmissions(patientId, startDate, endDate).subscribe(fetchedAdmissionsResponse -> {

                                                    // fetch metadata for AMC
                                                    Disposable x = httpClientService.getAmcMetaData().subscribe(amcMetaDataResponse -> {
                                                        JSONObject amcResponse = new JSONObject(amcMetaDataResponse);
                                                        JSONArray amcProgramsArray = amcResponse.getJSONArray("programs");

                                                        List<Map<String, Object>> amcEventsList = new ArrayList<>();

                                                        // Populate data values
                                                        List<Map<String, Object>> amcDataValuesList = new ArrayList<>();

                                                        // Iterate over programs
                                                        for (int k = 0; k < amcProgramsArray.length(); k++) {
                                                            JSONObject amcProgramObject = amcProgramsArray.getJSONObject(k);

                                                            // Get program ID
                                                            String amcProgramId = amcProgramObject.getString("id");

                                                            // Iterate over program stages
                                                            JSONArray amcProgramStagesArray = amcProgramObject.getJSONArray("programStages");
                                                            for (int stageIndex = 0; stageIndex < amcProgramStagesArray.length(); stageIndex++) {
                                                                JSONObject amcProgramStageObject = amcProgramStagesArray.getJSONObject(stageIndex);
                                                                String programStageId = amcProgramStageObject.getString("id");

                                                                JSONArray amcProgramStageDataElementsArray = amcProgramStageObject.getJSONArray("programStageDataElements");

                                                                for (int j = 0; j < amcProgramStageDataElementsArray.length(); j++) {
                                                                    JSONObject amcProgramStageDataElementObject = amcProgramStageDataElementsArray.getJSONObject(j);
                                                                    JSONObject amcDataElementObject = amcProgramStageDataElementObject.getJSONObject("dataElement");

                                                                    String amcDisplayName = amcDataElementObject.getString("displayName");
                                                                    String amcId = amcDataElementObject.getString("id");

                                                                    // Construct AMC payload
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

                                                                    Map<String, Object> amcPayload = new HashMap<>();
                                                                    amcPayload.put("occurredAt", visit.path("visit_date").asText());
                                                                    amcPayload.put("notes", new ArrayList<>());
                                                                    amcPayload.put("program", amcProgramId);
                                                                    amcPayload.put("programStage", programStageId);
                                                                    amcPayload.put("orgUnit", Constants.FIND_AMS_ORG_UNIT);


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
                                                                    if ("Department".equals(amcDisplayName)) {
                                                                        departmentData.put("dataElement", amcId);
                                                                        departmentData.put("value", department);
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

                                                                    if ("Daily number of admissions".equals(amcDisplayName)) {
                                                                        Map<String, Object> totalAdmissionsData = new HashMap<>();
                                                                        totalAdmissionsData.put("dataElement", amcId);
                                                                        totalAdmissionsData.put("value", totalAdmissions);
                                                                        amcDataValuesList.add(totalAdmissionsData);
                                                                    }

                                                                    // Add data values to the list
                                                                    amcPayload.put("dataValues", amcDataValuesList);
                                                                    amcEventsList.add(amcPayload);
                                                                }
                                                            }
                                                        }

                                                        // Post AMC events to DHIS2
                                                        Map<String, Object> amcEventPayload = new HashMap<>();
                                                        amcEventPayload.put("events", amcEventsList);

                                                        try {
                                                            String amcPayloadToPost = objectMapper.writeValueAsString(amcEventPayload);
                                                            httpClientService.postAmcEventProgram(amcPayloadToPost).doOnError(error -> {
                                                                log.error("Error occurred from DHIS2: {}", error.getMessage());
                                                            }).subscribe(AmcDhisResponse -> {
                                                                log.info("DHIS2 response FOR AMC: {}", AmcDhisResponse);
                                                            });
                                                        } catch (JsonProcessingException e) {
                                                            throw new RuntimeException(e);
                                                        }
                                                    }, Throwable::printStackTrace);
                                                }, Throwable::printStackTrace);

                                            });

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

}