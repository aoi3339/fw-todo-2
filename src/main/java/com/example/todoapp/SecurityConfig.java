package com.example.todoapp;

import java.util.LinkedHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
        entryPoints.put(PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(entryPoints);
        entryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));

        http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/css/**", "/fonts/zen-maru-gothic/**", "/favicon.ico", "/login", "/setup", "/error").permitAll()
                    // MCP authentication is deferred at the user's request.
                    .requestMatchers("/mcp", "/mcp/**").permitAll()
                    .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/mcp", "/mcp/**"))
                .formLogin(form -> form.loginPage("/login")
                    .defaultSuccessUrl("/todos", true)
                    .failureUrl("/login?error=true").permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout=true"))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
