// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MpdiaApplication {
    public static void main(String[] args) {
        SpringApplication.run(MpdiaApplication.class, args);
    }
}
