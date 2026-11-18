package com.barber.booking;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, LoginAttemptService attempts)
            throws Exception {
        http
            .authorizeHttpRequests(a -> a
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/panel.html", "/favicon.ico",
                        "/api/csrf").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/shops", "/api/shops/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/shops/*/windows/*/bookings", "/api/login",
                        "/api/logout").permitAll()
                .requestMatchers("/api/panel/**").hasRole("BARBER")
                .anyRequest().denyAll())
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/api/shops/*/windows/*/bookings"))
            .formLogin(f -> f
                .loginPage("/api/login")
                .loginProcessingUrl("/api/login")
                .successHandler((req, res, auth) -> {
                    attempts.succeeded(LoginThrottleFilter.key(req));
                    res.setStatus(HttpStatus.OK.value());
                })
                .failureHandler((req, res, ex) -> {
                    attempts.failed(LoginThrottleFilter.key(req));
                    res.setStatus(HttpStatus.UNAUTHORIZED.value());
                }))
            .logout(l -> l
                .logoutUrl("/api/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpStatus.OK.value())))
            .addFilterBefore(new LoginThrottleFilter(attempts),
                    UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(e -> e.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
