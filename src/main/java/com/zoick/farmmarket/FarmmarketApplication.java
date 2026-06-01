package com.zoick.farmmarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FarmmarketApplication {

	public static void main(String[] args) {
		SpringApplication.run(FarmmarketApplication.class, args);
	}
}