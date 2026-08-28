package com.mypetadmin.ps_gateway.config;

import com.mypetadmin.ps_gateway.filter.OnboardingHeadersGatewayFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    RouteLocator gatewayRoutes(
            RouteLocatorBuilder builder,
            OnboardingHeadersGatewayFilter onboardingHeadersGatewayFilter,
            @Value("${app.services.login-url}") String psLoginUrl,
            @Value("${app.services.orchestrator-url}") String psOrchestratorUrl) {
        return builder.routes()
                .route("auth-login", route -> route.path("/api/auth/login")
                        .filters(filter -> filter.setPath("/auth/login"))
                        .uri(psLoginUrl))
                .route("auth-activation", route -> route.path("/api/auth/activation")
                        .filters(filter -> filter.setPath("/auth/activation"))
                        .uri(psLoginUrl))
                .route("auth-password-forgot", route -> route.path("/api/auth/password/forgot")
                        .filters(filter -> filter.setPath("/auth/password/forgot"))
                        .uri(psLoginUrl))
                .route("auth-password-reset", route -> route.path("/api/auth/password/reset")
                        .filters(filter -> filter.setPath("/auth/password/reset"))
                        .uri(psLoginUrl))
                .route("auth-password-change", route -> route.path("/api/auth/password/change")
                        .filters(filter -> filter.setPath("/auth/password/change"))
                        .uri(psLoginUrl))
                .route("public-onboarding", route -> route.path("/api/onboardings")
                        .filters(filter -> filter
                                .filter(onboardingHeadersGatewayFilter)
                                .setPath("/internal/onboardings"))
                        .uri(psOrchestratorUrl))
                .build();
    }
}
