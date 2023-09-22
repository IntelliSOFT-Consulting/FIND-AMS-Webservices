package com.intellisoft.findams;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@EnableScheduling
@SpringBootApplication
public class FindamsApplication {

	public static void main(String[] args) {
		SpringApplication.run(FindamsApplication.class, args);
	}
}
