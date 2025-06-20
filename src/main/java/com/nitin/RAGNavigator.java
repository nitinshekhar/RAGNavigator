package com.nitin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
public class RAGNavigator {
    public static void main (String[] args){
        SpringApplication.run(RAGNavigator.class, args);
    }
}
