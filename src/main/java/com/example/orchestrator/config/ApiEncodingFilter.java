package com.example.orchestrator.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Приводит Content-Type API-ответов к application/json;charset=UTF-8. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiEncodingFilter extends OncePerRequestFilter {

    private static final String JSON_UTF8 = "application/json;charset=UTF-8";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/api/")) {
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(JSON_UTF8);
        }
        filterChain.doFilter(request, response);
    }
}
