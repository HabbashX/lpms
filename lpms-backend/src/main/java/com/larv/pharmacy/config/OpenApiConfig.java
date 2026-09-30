package com.larv.pharmacy.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI pharmacyOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Pharmacy Management System API")
                        .description("""
                                REST API for pharmacy inventory, sales, customer debt and reporting.

                                All endpoints are prefixed with `/api/v1`.
                                Authenticate via `POST /api/v1/auth/login` and send the returned
                                `accessToken` as `Authorization: Bearer <token>`.""")
                        .version("1.0.0")
                        .contact(new Contact().name("LPMS")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
