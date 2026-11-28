package me.blindle.accounts;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Tout le site reste ouvert sans compte (jouer, rejoindre une room). Seules l'administration des
 * personnages ({@code /api/admin/curation/**}, rôle admin) et la modification de son profil
 * demandent une connexion. Les retraits de thème ({@code /api/admin/themes/**}) gardent leur jeton
 * {@code X-Admin-Token}. Pas de redirection vers une page de connexion : une API renvoie 401.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthProviders providers,
                                                   AccountUserService users) throws Exception {
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/admin/curation/**", "/api/admin/content/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/me").authenticated()
                        .anyRequest().permitAll())
                .csrf(c -> c
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        // jeton dans un en-tête, pas de cookie : pas de CSRF possible
                        .ignoringRequestMatchers("/api/admin/themes/**", "/ws/**"))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .logout(l -> l
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                // les images des cartes et le front sont des fichiers statiques : laisser le cache du navigateur
                .headers(h -> h.cacheControl(HeadersConfigurer.CacheControlConfig::disable))
                .requestCache(r -> r.disable());
        if (!providers.registrations().isEmpty()) {
            http.oauth2Login(o -> o
                    .clientRegistrationRepository(new InMemoryClientRegistrationRepository(providers.registrations()))
                    .userInfoEndpoint(u -> u.userService(users))
                    .defaultSuccessUrl("/", true)
                    .failureUrl("/?login=error"));
        }
        return http.build();
    }
}
