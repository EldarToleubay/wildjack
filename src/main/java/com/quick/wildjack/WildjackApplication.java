package com.quick.wildjack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WildjackApplication {

	public static void main(String[] args) {
		SpringApplication.run(WildjackApplication.class, args);
	}

}
