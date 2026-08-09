package com.synx.devkit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DevKitApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevKitApplication.class, args);
    }

}
