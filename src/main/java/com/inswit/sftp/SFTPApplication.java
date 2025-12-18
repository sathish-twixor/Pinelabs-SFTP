package com.inswit.sftp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SFTPApplication {

	public static void main(String[] args) {
		SpringApplication.run(SFTPApplication.class, args);
	}

}
