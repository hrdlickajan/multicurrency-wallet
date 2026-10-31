package com.github.hrdlickajan.multicurrencywallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@SpringBootApplication
@EnableScheduling
public class MulticurrencyWalletApplication {

    static void main(String[] args) {
        SpringApplication.run(MulticurrencyWalletApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Europe/Prague"));
    }
}
