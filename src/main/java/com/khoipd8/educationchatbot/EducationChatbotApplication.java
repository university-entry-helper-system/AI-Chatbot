package com.khoipd8.educationchatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class EducationChatbotApplication {

	public static void main(String[] args) {
		SpringApplication.run(EducationChatbotApplication.class, args);
	}

}
