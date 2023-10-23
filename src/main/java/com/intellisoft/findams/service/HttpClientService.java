package com.intellisoft.findams.service;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.List;

@Service
public class HttpClientService {
    private final WebClient webClient;

    @Value("${ams.funsoft.antibiotic-prescriptions-url}")
    private String funsoftApiUrl;

    @Value("${ams.funsoft.daily-admissions-url}")
    private String dailyAdmissionsUrl;

    @Value("${ams.dhis.username}")
    private String username;

    @Value("${ams.dhis.password}")
    private String password;

    public HttpClientService(WebClient.Builder webClientBuilder, @Value("${ams.whonet-data-upload-url}") String whonetDataUploadUrl) {
        this.webClient = webClientBuilder.baseUrl(whonetDataUploadUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString((
                        username + ":" + password).getBytes()))
                .build();
    }

    public Mono<String> postToDhis(List<JSONObject> jsonArray) {
        return webClient.post()
                .body(BodyInserters.fromValue(jsonArray.toString()))
                .retrieve()
                .bodyToMono(String.class);
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
}



