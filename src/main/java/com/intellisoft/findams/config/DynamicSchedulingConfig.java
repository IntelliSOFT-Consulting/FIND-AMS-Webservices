package com.intellisoft.findams.config;


import com.intellisoft.findams.service.FileParsingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class DynamicSchedulingConfig implements SchedulingConfigurer {

    private static final String WHO_NET_FILE_PATH = "whonet/WHONET.txt";

    @Autowired
    FileParsingService fileParsingService;

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

        // Schedule the request to read the file every 2 minutes
        taskRegistrar.addTriggerTask(
                () -> {
                    String filePath = WHO_NET_FILE_PATH;
                    fileParsingService.parseFile(filePath);
                },
                triggerContext -> {
                    // Calculate the next execution time based on the current time and a 2-minute interval
                    // interval to be changed >>>>
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
    }
}
