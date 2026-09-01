// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProdoxApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProdoxApplication.class, args);
    }
}
