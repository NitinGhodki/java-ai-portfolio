package com.aiportfolio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class JavaAiWeekAApplication {

	public static void main(String[] args) {
		SpringApplication.run(JavaAiWeekAApplication.class, args);
	}

}
