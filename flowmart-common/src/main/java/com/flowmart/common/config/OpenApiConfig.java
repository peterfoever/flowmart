package com.flowmart.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档。
 * <p>
 * 访问 http://localhost:8080/doc.html （Knife4j UI）。
 * 生产环境通过 {@code springdoc.api-docs.enabled=false} 关闭，见 application-prod.yml。
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:flowmart}")
    private String applicationName;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Bean
    public OpenAPI flowmartOpenApi() {
        return new OpenAPI().info(new Info()
                .title("flowmart 电商供应链履约中台 API")
                .description("当前环境: " + activeProfile + " / 应用: " + applicationName)
                .version("1.0.0")
                .contact(new Contact().name("flowmart team")));
    }
}
