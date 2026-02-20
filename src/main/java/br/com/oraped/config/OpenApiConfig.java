package br.com.oraped.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI openAPI() {
    return new OpenAPI()
      .info(new Info()
        .title("OraPed API")
        .version("v1")
        .description("Documentação da API do OraPed"));
  }
}