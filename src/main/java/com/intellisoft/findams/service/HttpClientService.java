package com.intellisoft.findams.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellisoft.findams.dto.FileParseSummaryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URISyntaxException;
import java.util.*;

@Slf4j
@Service
public class HttpClientService {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${ams.funsoft.amu-url}")
    private String amuApiUrl;

    @Value("${ams.funsoft.amc-url}")
    private String amcUrl;

    @Value("${ams.dhis.username}")
    private String username;

    @Value("${ams.dhis.password}")
    private String password;

    @Value("${ams.trackedEntityAttributes-url}")
    private String trackedEntityAttributes;

    @Value("${ams.whonet-data-upload-url}")
    private String whonetUploadUrl;

    @Value("${ams.datastore-url}")
    private String datastoreUrl;

    @Value("${ams.event-program-url}")
    private String eventProgramUrl;

    @Value("${ams.option-set-url}")
    private String optionSetUrl;

    @Value("${ams.atc-codes-url}")
    private String atcCodesUrl;

    @Value("${ams.org-units-url}")
    private String orgUnitsUrl;

    @Value("${ams.tracked-entity-types-url}")
    private String trackedEntityUrl;

    @Value("${ams.amu-program-metadata-url}")
    private String amuMetaDataUrl;

    @Value("${ams.amc-program-metadata-url}")
    private String amcMetaDataUrl;

    @Value("${ams.enrollments-url}")
    private String enrollmentsUrl;

    @Value("${ams.program-stage-attributes-url}")
    private String programStageAtrributesUrl;

    @Value("${ams.events-api-url}")
    private String eventsApiUrl;

    @Value("${ams.aware-class-url}")
    private String awareUrl;

    public HttpClientService(WebClient.Builder webClientBuilder, @Value("${whonet-data-upload-url}") String whonetDataUploadUrl, @Value("${ams.dhis.username}") String username, @Value("${ams.dhis.password}") String password, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.baseUrl(whonetDataUploadUrl).defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes())).build();
    }


    public Mono<String> getPatientsAntibioticPrescriptions(String patientId, String startDate, String endDate) {
        String apiUrl = amuApiUrl + "patient_id=" + patientId + "&startDate=" + startDate + "&endDate=" + endDate;
        return webClient.get().uri(apiUrl).retrieve().bodyToMono(String.class);
    }

    public Mono<String> fetchDailyAdmissions(String patientId, String startDate, String endDate) {
        String apiUrl = amcUrl + "patient_id=" + patientId + "&startDate=" + startDate + "&endDate=" + endDate;
        return webClient.get().uri(apiUrl).retrieve().bodyToMono(String.class);
    }


    public Mono<JsonNode> fetchTrackedEntityAttributes() {
        String apiUrl = trackedEntityAttributes;
        return webClient.get().uri(apiUrl).retrieve().bodyToMono(JsonNode.class).doOnNext(response -> {
        });
    }

    public Mono<String> postTrackedEntityInstances(Map<String, Object> trackedEntityInstancePayload) {
        String apiUrl = whonetUploadUrl;
        ObjectMapper objectMapper = new ObjectMapper();
        String payloadJson;

        try {
            payloadJson = objectMapper.writeValueAsString(trackedEntityInstancePayload);
        } catch (JsonProcessingException e) {
            return Mono.just("{'error':'Failed to convert payload to JSON'}");
        }

        return webClient.post().uri(apiUrl).contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(payloadJson)).exchangeToMono(response -> {
            if (response.statusCode().is2xxSuccessful()) {
                return response.bodyToMono(String.class);
            } else {
                return response.bodyToMono(String.class).flatMap(body -> {
                    log.debug("Error occurred while posting TrackedEntityInstances to DHIS2: {}", body);
                    return Mono.just(body);
                });
            }
        });
    }

    public Mono<String> postToDhis2DataStore(FileParseSummaryDto fileParseSummaryDto) throws URISyntaxException, JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        return getFromDhis2DataStore().flatMap(existingData -> {
            existingData.add(fileParseSummaryDto);
            String payloadJson = null;
            try {
                payloadJson = objectMapper.writeValueAsString(existingData);

            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            return updateDhis2DataStore(payloadJson);
        });
    }

    public Mono<List<FileParseSummaryDto>> getFromDhis2DataStore() {

        String apiUrl = datastoreUrl + "/keyDefaultLayoutLocked";
        return webClient.get().uri(apiUrl).retrieve().bodyToMono(new ParameterizedTypeReference<List<FileParseSummaryDto>>() {
        }).onErrorResume(e -> {
            log.debug("Error while getting data from DHIS2 DataStore: {}", e.getMessage());
            return Mono.just(Collections.emptyList());
        });
    }

    public Mono<String> updateDhis2DataStore(String payloadJson) {

        String apiUrl = datastoreUrl + "/keyDefaultLayoutLocked";

        return webClient.method(HttpMethod.PUT).uri(apiUrl).contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(payloadJson)).retrieve().bodyToMono(String.class).onErrorResume(e -> {
            log.debug("Error while updating DHIS2 DataStore: {}", e.getMessage());
            return Mono.just("Error updating DHIS2 DataStore");
        });
    }


    public Mono<String> postAmuEventProgram(String finalPayload) {
        String apiUrl = eventProgramUrl;

        return webClient.post().uri(apiUrl).contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(finalPayload)).exchangeToMono(response -> {
            if (response.statusCode().is2xxSuccessful()) {
                return response.bodyToMono(String.class);
            } else {
                return response.bodyToMono(String.class).flatMap(body -> {
                    log.debug("Error occurred while posting AMU Event Program to DHIS2: {}", body);
                    return Mono.just(body);
                });
            }
        });
    }

    public Mono<String> postAmcEventProgram(String finalPayload) {
        String apiUrl = eventsApiUrl;

        return webClient.post().uri(apiUrl).contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(finalPayload)).exchangeToMono(response -> {
            if (response.statusCode().is2xxSuccessful()) {
                return response.bodyToMono(String.class);
            } else {
                return response.bodyToMono(String.class).flatMap(body -> {
                    log.debug("Error occurred while posting AMC Event Program to DHIS2: {}", body);
                    return Mono.just(body);
                });
            }
        });
    }

    public Mono<Map<String, Map<String, String>>> fetchOptionSets() {
        String apiUrl = optionSetUrl;

        return webClient.get().uri(apiUrl).retrieve().bodyToMono(JsonNode.class).map(response -> {
            Map<String, Map<String, String>> optionSetsMap = new HashMap<>();

            JsonNode optionSets = response.path("optionSets");
            for (JsonNode optionSet : optionSets) {
                String displayName = optionSet.path("displayName").asText();
                JsonNode options = optionSet.path("options");

                Map<String, String> optionsMap = new HashMap<>();

                for (JsonNode option : options) {
                    String code = option.path("code").asText();
                    String name = option.path("name").asText();

                    optionsMap.put(code, name);
                }
                optionSetsMap.put(displayName, optionsMap);
            }

            return optionSetsMap;
        }).doOnError(error -> {
            log.debug("Error fetching option sets: {}", error.getMessage());
        });
    }


    public Mono<Double> fetchDddValue(String medicationName) {
        String apiUrl = atcCodesUrl;

        return webClient.get().uri(apiUrl).retrieve().bodyToMono(JsonNode.class).flatMap(dataStoreResponse -> {
            List<JsonNode> dddDataList = dataStoreResponse.findValues("Name");

            for (int i = 0; i < dddDataList.size(); i++) {
                JsonNode dddData = dddDataList.get(i);
                if (medicationName.equalsIgnoreCase(dddData.asText())) {
                    double dddValue = dataStoreResponse.findValues("DDD").get(i).asDouble();
                    return Mono.just(dddValue);
                }
            }

            log.warn("Medication Name not found in DHIS2 response: {}", medicationName);
            return Mono.empty();
        }).defaultIfEmpty(0.0);
    }

    public Mono<String> getAmuMetaData() {
        String apiUrl = amuMetaDataUrl;
        return webClient.get().uri(apiUrl).retrieve().bodyToMono(String.class);
    }

    public Mono<String> getAmcMetaData() {
        String apiUrl = amcMetaDataUrl;
        return webClient.get().uri(apiUrl).retrieve().bodyToMono(String.class);
    }

    public Mono<String> postEnrollmentToDhis(String enrollmentReqPayload) {
        String apiUrl = enrollmentsUrl;

        return webClient.post().uri(apiUrl).contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(enrollmentReqPayload)).exchangeToMono(response -> {
            if (response.statusCode().is2xxSuccessful()) {
                return response.bodyToMono(String.class);
            } else {
                return response.bodyToMono(String.class).flatMap(body -> {
                    log.debug("Error occurred while posting enrollment for tracked entity to DHIS2: {}", body);
                    return Mono.just(body);
                });
            }
        });
    }

    public Mono<String> postEventToDhis(String enrollmentReqPayload) throws JsonProcessingException {
        String apiUrl = eventsApiUrl;

        return webClient.post().uri(apiUrl).contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(enrollmentReqPayload)).exchangeToMono(response -> {
            if (response.statusCode().is2xxSuccessful()) {
                return response.bodyToMono(String.class);
            } else {
                return response.bodyToMono(String.class).flatMap(body -> {
                    log.debug("Error occurred while posting event for enrollment to DHIS2: {}", body);
                    return Mono.just(body);
                });
            }
        });
    }

}