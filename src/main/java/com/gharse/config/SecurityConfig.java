package com.gharse.config;

import com.gharse.auth.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtUtil jwt) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/v1/health", "/actuator/health").permitAll()
                        .requestMatchers("/v1/auth/**").permitAll()
                        .requestMatchers("/v1/payments/webhook").permitAll()
                        // Order matters: these must come before the public seller-profile rule below.
                        .requestMatchers("/v1/seller/me", "/v1/seller/me/**").authenticated()
                        // Public browsing (FR5–FR10, FR24)
                        .requestMatchers(HttpMethod.GET,
                                "/v1/products/**", "/v1/categories", "/v1/seller/*").permitAll()
                        // Product photos are public so buyers can see them.
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthFilter(jwt), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeError(res, HttpServletResponse.SC_UNAUTHORIZED, "Please log in to continue."))
                        .accessDeniedHandler((req, res, e) ->
                                writeError(res, HttpServletResponse.SC_FORBIDDEN, "You don't have permission to do that.")));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Filter-chain errors never reach @RestControllerAdvice, so they are written here
    // in the same ApiResponse shape the frontend expects. Messages are fixed constants.
    private static void writeError(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write("{\"success\":false,\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
