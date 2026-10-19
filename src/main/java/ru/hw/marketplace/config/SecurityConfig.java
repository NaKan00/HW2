package ru.hw.marketplace.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.hw.marketplace.security.*;
import ru.hw.marketplace.exception.Errors;
import ru.hw.marketplace.generated.model.ErrorCode;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecurityFilterChain security(HttpSecurity http, JwtService jwt, ObjectMapper mapper) throws Exception {
        return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c -> c.disable()).formLogin(f -> f.disable()).httpBasic(b -> b.disable())
            .authorizeHttpRequests(a -> a.requestMatchers("/auth/register", "/auth/login", "/auth/refresh").permitAll().anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                res.setStatus(401); res.setContentType("application/json");
                mapper.writeValue(res.getOutputStream(), Errors.body(ErrorCode.TOKEN_INVALID, "Authentication required", Map.of()));
            }).accessDeniedHandler((req, res, ex) -> {
                res.setStatus(403); res.setContentType("application/json");
                mapper.writeValue(res.getOutputStream(), Errors.body(ErrorCode.ACCESS_DENIED, "Access denied", Map.of()));
            }))
            .addFilterBefore(new JwtFilter(jwt, mapper), UsernamePasswordAuthenticationFilter.class).build();
    }
}
