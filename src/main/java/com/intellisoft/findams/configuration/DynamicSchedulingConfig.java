package com.intellisoft.findams.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellisoft.findams.constants.Constants;
import com.intellisoft.findams.service.EventProgramService;
import com.intellisoft.findams.service.HttpClientService;
import com.intellisoft.findams.service.MicrobiologyService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Configuration
public class DynamicSchedulingConfig implements SchedulingConfigurer {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Autowired
    MicrobiologyService microbiologyService;
    @Autowired
    HttpClientService httpClientService;
    @Autowired
    EventProgramService eventProgramService;
    @Value("${ams.last-event-created-url}")
    private String lastEventCreatedUrl;
    @Value("${ams.dhis.username}")
    private String username;
    @Value("${ams.dhis.password}")
    private String password;

    public DynamicSchedulingConfig(WebClient.Builder webClientBuilder, @Value("${ams.last-event-created-url}") String lastEventCreatedUrl, @Value("${ams.dhis.username}") String username, @Value("${ams.dhis.password}") String password, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.baseUrl(lastEventCreatedUrl).defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes())).build();
    }


    @Bean
    public TaskScheduler poolScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("ThreadPoolTaskScheduler");
        scheduler.setPoolSize(1);
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(poolScheduler());

        taskRegistrar.addTriggerTask(() -> {
            File directory = new File(Constants.WHONET_FILE_PATH);
            File[] files = directory.listFiles();

            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        try {
                            String filePath = file.getAbsolutePath();
                            String fileContent = new String(Files.readAllBytes(Paths.get(filePath)));
                            String fileName = file.getName();

                            microbiologyService.parseFile(filePath, fileContent, fileName);

                        } catch (FileNotFoundException e) {
                            log.error("File not found: " + file.getAbsolutePath());
                        } catch (IOException e) {
                            log.error("Error reading file: " + file.getAbsolutePath(), e);
                        }
                    }
                }
            }
        }, triggerContext -> {
            Date lastExecutionTime = triggerContext.lastActualExecutionTime();
            if (lastExecutionTime == null) {
                lastExecutionTime = new Date();
            }

            LocalDateTime midnight = LocalDateTime.now().with(LocalTime.MIDNIGHT);
            ZonedDateTime nextExecutionTime;

            if (lastExecutionTime.before(Date.from(midnight.atZone(ZoneId.systemDefault()).toInstant()))) {
                nextExecutionTime = midnight.atZone(ZoneId.systemDefault());
            } else {
                nextExecutionTime = midnight.plusDays(1).atZone(ZoneId.systemDefault());
            }
            return nextExecutionTime.toInstant();
        });

        // Schedule a task to fetch AMU/AMC data from FUNSOFT HMIS
        taskRegistrar.addTriggerTask(() -> {
            httpClientService.fetchLastCreatedEvent().doOnError(error -> {
                log.debug("Error occurred {}", error.getMessage());
            }).subscribe(response -> {

                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    JSONArray instancesArray = jsonResponse.getJSONArray("instances");

                    if (instancesArray.length() >= 1) {
                        for (int i = 0; i < instancesArray.length(); i++) {
                            JSONObject instanceObject = instancesArray.getJSONObject(i);
                            String createdAt = instanceObject.getString("createdAt");

                            DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
                            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                            LocalDateTime date = LocalDateTime.parse(createdAt, inputFormatter);

                            String startDate = date.format(outputFormatter);
                            String endDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                            eventProgramService.fetchFromFunSoft(startDate, endDate);
                        }
                    } else {
                        String startDate = "2022-04-28";
                        String endDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                        eventProgramService.fetchFromFunSoft(startDate, endDate);
                    }
                } catch (Exception e) {
                    log.debug("Error processing JSON response: {}", e.getMessage());
                }
            });

        }, triggerContext -> {
            Date lastExecutionTime = triggerContext.lastActualExecutionTime();
            if (lastExecutionTime == null) {
                lastExecutionTime = new Date();
            }

            LocalDateTime midnight = LocalDateTime.now().with(LocalTime.MIDNIGHT);
            ZonedDateTime nextExecutionTime;

            if (lastExecutionTime.before(Date.from(midnight.atZone(ZoneId.systemDefault()).toInstant()))) {
                nextExecutionTime = midnight.atZone(ZoneId.systemDefault());
            } else {
                nextExecutionTime = midnight.plusDays(1).atZone(ZoneId.systemDefault());
            }
            return nextExecutionTime.toInstant();
        });
    }
}
