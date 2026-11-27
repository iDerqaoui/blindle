package me.blindle.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Pas de comptes « mot de passe » : la connexion passe par Google / Discord (module accounts). */
@SpringBootApplication(scanBasePackages = "me.blindle", exclude = UserDetailsServiceAutoConfiguration.class)
@EntityScan("me.blindle")
@EnableJpaRepositories("me.blindle")
public class BlindleApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlindleApplication.class, args);
    }
}
