package com.example.orgmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrgManagerApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrgManagerApplication.class, args);
    }
}
