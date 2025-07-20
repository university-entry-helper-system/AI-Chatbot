package com.khoipd8.educationchatbot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Education Chatbot API")
                        .description("API cho hệ thống chatbot tư vấn tuyển sinh đại học")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("KhoiPD8")
                                .email("khoipd8@gmail.com")
                                .url("https://github.com/khoipd8"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Development server"),
                        new Server()
                                .url("https://api.education-chatbot.com")
                                .description("Production server")
                ));
    }
} 