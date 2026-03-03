package com.example.speechapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SpeechDiarizationApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpeechDiarizationApplication.class, args);
    }
}