package com.intellisoft.findams.configuration;

import com.intellisoft.findams.constants.Constants;
import com.intellisoft.findams.service.EventProgramService;
import com.intellisoft.findams.service.MicrobiologyService;
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
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class DynamicSchedulingConfig implements SchedulingConfigurer {
    @Autowired
    MicrobiologyService microbiologyService;
    @Autowired
    EventProgramService eventProgramService;

    private Instant lastExecutionTime = Instant.now();

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

        // Schedule the request to read and parse WHONET files in the directory every 2 minutes or x hours
        taskRegistrar.addTriggerTask(() -> {
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
                            String fileName = file.getName();

                            // process file content
//                            microbiologyService.parseFile(filePath, fileContent, fileName);
                        } catch (FileNotFoundException e) {
                            log.error("File not found: " + file.getAbsolutePath());
                        } catch (IOException e) {
                            log.error("Error reading file: " + file.getAbsolutePath(), e);
                        }
                    }
                }
            }
        }, triggerContext -> {
            // Calculate the next execution time based on the last execution time and a 2-minute interval
            Date lastExecutionTime = triggerContext.lastActualExecutionTime();
            if (lastExecutionTime == null) {
                lastExecutionTime = new Date();
            }
            long twoMinutesInMillis = TimeUnit.MINUTES.toMillis(10080);
            Date nextExecutionTime = new Date(lastExecutionTime.getTime() + twoMinutesInMillis);
            return nextExecutionTime.toInstant();
        });

        // Schedule the request to fetch AMU/AMC data from FUNSOFT HMIS
        taskRegistrar.addTriggerTask(() -> {
            log.info("Starting AMU/AMC data fetch task at {}", Instant.now());

            // fetch data based on the last execution time
            eventProgramService.fetchFromFunSoftBasedOnExecutionTime(lastExecutionTime);

            log.info("Finished AMU/AMC data fetch task at {}", Instant.now());
        }, triggerContext -> {
            // Calculate the next execution time based on the last execution time
            Instant nextExecutionTime = lastExecutionTime.plus(1, TimeUnit.MINUTES.toChronoUnit());
            lastExecutionTime = nextExecutionTime;
            return Date.from(nextExecutionTime).toInstant();
        });
    }
}
