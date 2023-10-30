package com.intellisoft.findams.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellisoft.findams.dto.FileParseSummaryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
public class HttpClientService {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${ams.funsoft.antibiotic-prescriptions-url}")
    private String funsoftApiUrl;

    @Value("${ams.funsoft.daily-admissions-url}")
    private String dailyAdmissionsUrl;

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

    public HttpClientService(WebClient.Builder webClientBuilder,
                             @Value("${ams.whonet-data-upload-url}") String whonetDataUploadUrl,
                             @Value("${ams.dhis.username}") String username,
                             @Value("${ams.dhis.password}") String password, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.baseUrl(whonetDataUploadUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes()))
                .build();
    }


    public Mono<String> getPatientsAntibioticPrescriptions(String patientId, String startDate, String endDate) {
        String apiUrl = funsoftApiUrl + "patient_id=" + patientId + "&startDate=" + startDate + "&endDate=" + endDate;
        return webClient.get()
                .uri(apiUrl)
                .retrieve()
                .bodyToMono(String.class);
    }

    public Mono<String> fetchDailyAdmissions(String patientId, String startDate, String endDate) {
        String apiUrl = dailyAdmissionsUrl + "patient_id=" + patientId + "&startDate=" + startDate + "&endDate=" + endDate;
        return webClient.get()
                .uri(apiUrl)
                .retrieve()
                .bodyToMono(String.class);
    }


    public Mono<JsonNode> fetchTrackedEntityAttributes() {
        String apiUrl = trackedEntityAttributes;
        return webClient.get()
                .uri(apiUrl)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnNext(response -> {
                    System.out.println("Response: " + response);
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

        return webClient.post()
                .uri(apiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(payloadJson))
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(String.class);
                    } else {
                        return response.bodyToMono(String.class).flatMap(body -> {
                            log.error("Error occurred while posting TrackedEntityInstances to DHIS2: {}", body);
                            return Mono.just(body);
                        });
                    }
                });
    }

    public Disposable postToDhis2DataStore(FileParseSummaryDto fileParseSummaryDto) {
        String apiUrl = datastoreUrl;
        ObjectMapper objectMapper = new ObjectMapper();
        String payloadJson;

        try {
            payloadJson = objectMapper.writeValueAsString(fileParseSummaryDto);
        } catch (JsonProcessingException e) {
            log.error("Error while converting FileParseSummaryDto to JSON: {}", e.getMessage());
            return Mono.error(e).subscribe();
        }

        return webClient.post()
                .uri(apiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(payloadJson))
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(error -> {
                    log.error("Error occurred while posting to DHIS2 DataStore: {}", error.getMessage());
                })
                .subscribe(response -> {
                    log.info("Response from DHIS2 DataStore: {}", response);
                });
    }
}