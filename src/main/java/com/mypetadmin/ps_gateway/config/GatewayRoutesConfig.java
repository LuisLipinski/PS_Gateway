package com.mypetadmin.ps_gateway.config;

import com.mypetadmin.ps_gateway.filter.OnboardingHeadersGatewayFilter;
import com.mypetadmin.ps_gateway.filter.UserContextHeadersGatewayFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
public class GatewayRoutesConfig {

    private static final DataSize MAX_JSON_REQUEST_SIZE = DataSize.ofKilobytes(64);

    @Bean
    RouteLocator gatewayRoutes(
            RouteLocatorBuilder builder,
            OnboardingHeadersGatewayFilter onboardingHeadersGatewayFilter,
            UserContextHeadersGatewayFilter userContextHeadersGatewayFilter,
            @Value("${app.services.login-url}") String psLoginUrl,
            @Value("${app.services.orchestrator-url}") String psOrchestratorUrl,
            @Value("${app.services.user-url}") String psUserUrl,
            @Value("${app.services.contrato-url}") String psContratoUrl) {
        return builder.routes()
                .route("auth-login", route -> route.path("/api/auth/login")
                        .filters(filter -> filter
                                .setRequestSize(MAX_JSON_REQUEST_SIZE)
                                .setPath("/auth/login"))
                        .uri(psLoginUrl))
                .route("auth-activation", route -> route.path("/api/auth/activation")
                        .filters(filter -> filter
                                .setRequestSize(MAX_JSON_REQUEST_SIZE)
                                .setPath("/auth/activation"))
                        .uri(psLoginUrl))
                .route("auth-refresh", route -> route.path("/api/auth/refresh")
                        .filters(filter -> filter
                                .setRequestSize(MAX_JSON_REQUEST_SIZE)
                                .setPath("/auth/refresh"))
                        .uri(psLoginUrl))
                .route("auth-logout", route -> route.path("/api/auth/logout")
                        .filters(filter -> filter
                                .setRequestSize(MAX_JSON_REQUEST_SIZE)
                                .setPath("/auth/logout"))
                        .uri(psLoginUrl))
                .route("auth-password-forgot", route -> route.path("/api/auth/password/forgot")
                        .filters(filter -> filter
                                .setRequestSize(MAX_JSON_REQUEST_SIZE)
                                .setPath("/auth/password/forgot"))
                        .uri(psLoginUrl))
                .route("auth-password-reset", route -> route.path("/api/auth/password/reset")
                        .filters(filter -> filter
                                .setRequestSize(MAX_JSON_REQUEST_SIZE)
                                .setPath("/auth/password/reset"))
                        .uri(psLoginUrl))
                .route("auth-password-change", route -> route.path("/api/auth/password/change")
                        .filters(filter -> filter
                                .setRequestSize(MAX_JSON_REQUEST_SIZE)
                                .setPath("/auth/password/change"))
                        .uri(psLoginUrl))
                .route("public-onboarding", route -> route.path("/api/onboardings")
                        .filters(filter -> filter
                                .setRequestSize(MAX_JSON_REQUEST_SIZE)
                                .filter(onboardingHeadersGatewayFilter)
                                .setPath("/internal/onboardings"))
                        .uri(psOrchestratorUrl))
                .route("users-root", route -> route.path("/api/users")
                        .filters(filter -> filter
                                .setRequestSize(MAX_JSON_REQUEST_SIZE)
                                .filter(userContextHeadersGatewayFilter)
                                .setPath("/internal/usuarios"))
                        .uri(psUserUrl))
                .route("users-nested", route -> route.path("/api/users/**")
                        .filters(filter -> filter
                                .setRequestSize(MAX_JSON_REQUEST_SIZE)
                                .filter(userContextHeadersGatewayFilter)
                                .rewritePath("/api/users/(?<segment>.*)", "/internal/usuarios/${segment}"))
                        .uri(psUserUrl))
                .route("contracts-tenant", route -> route.path("/api/contracts")
                        .and().method("GET")
                        .filters(filter -> filter
                                .filter(userContextHeadersGatewayFilter)
                                .setPath("/contratos/tenant"))
                        .uri(psContratoUrl))
                .build();
    }
}
