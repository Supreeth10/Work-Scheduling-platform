package com.vorto.challenge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.*;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String id = UUID.randomUUID().toString();
        MDC.put("correlationId", id);
        try {
            ((HttpServletResponse) response).setHeader("X-Correlation-Id", id);
            chain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
