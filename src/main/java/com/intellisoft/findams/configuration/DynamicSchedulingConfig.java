package com.intellisoft.findams.configuration;


import com.intellisoft.findams.constants.Constants;
import com.intellisoft.findams.service.FileParsingService;
import com.intellisoft.findams.service.FunsoftService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class DynamicSchedulingConfig implements SchedulingConfigurer {
    @Autowired
    FileParsingService fileParsingService;
    @Autowired
    FunsoftService funsoftService;

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

        // Schedule the request to read and parse WHONET files in the directory every 2 minutes // or x hours
        taskRegistrar.addTriggerTask(
                () -> {
                    // Get a list of files in the directory
                    File directory = new File(Constants.WHONET_FILE_PATH);
                    File[] files = directory.listFiles();

                    if (files != null) {
                        for (File file : files) {
                            if (file.isFile()) {
                                try {
                                    // Read the content of each file
                                    String filePath = file.getAbsolutePath();
                                    String fileContent = new String(Files.readAllBytes(Paths.get(filePath)));

                                    // process file content
                                    fileParsingService.parseFile(filePath, fileContent);
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                    log.error("File not found: " + file.getAbsolutePath());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                },
                triggerContext -> {
                    // Calculate the next execution time based on the current time and a 2-minute interval
                    Date lastExecutionTime = triggerContext.lastActualExecutionTime();
                    if (lastExecutionTime == null) {
                        lastExecutionTime = new Date();
                    }
                    long twoMinutesInMillis = TimeUnit.MINUTES.toMillis(1);
                    Date nextExecutionTime = new Date(lastExecutionTime.getTime() + twoMinutesInMillis);

                    log.info("Next File Parse scheduled time -> {}", nextExecutionTime);
                    return nextExecutionTime.toInstant();
                }
        );

        // cron task 2:
        // Schedule the request to fetch Prescriptions data from an FUNSOFT HMIS
        taskRegistrar.addTriggerTask(
                () -> {
                    // Your logic to fetch data from the external API
                    funsoftService.getPatientsAntibioticPrescriptions();
                },
                triggerContext -> {
                    // Calculate the next execution time for the external API task
                    Date lastExecutionTime = triggerContext.lastActualExecutionTime();
                    if (lastExecutionTime == null) {
                        lastExecutionTime = new Date();
                    }
                    long twoMinutesInMillis = TimeUnit.MINUTES.toMillis(1);
                    Date nextExecutionTime = new Date(lastExecutionTime.getTime() + twoMinutesInMillis);

                    log.info("Next Prescription Data fetch scheduled time -> {}", nextExecutionTime);

                    // Return the next execution time
                    return nextExecutionTime.toInstant();
                }
        );
    }
}
