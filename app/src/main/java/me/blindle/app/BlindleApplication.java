package me.blindle.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "me.blindle")
public class BlindleApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlindleApplication.class, args);
    }
}
