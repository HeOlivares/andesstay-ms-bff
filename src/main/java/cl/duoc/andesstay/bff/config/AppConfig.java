package cl.duoc.andesstay.bff.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig {

	@Bean
	WebClient.Builder webClientBuilder() {
		return WebClient.builder();
	}

	@Bean
	OpenAPI bffOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("AndesStay BFF API")
						.version("1.0")
						.description("Backend-for-Frontend: JWT validation, roles 401/403, proxy a catalog/reservations."))
				.addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
				.components(new Components().addSecuritySchemes("bearer-jwt",
						new SecurityScheme()
								.name("bearer-jwt")
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")));
	}

	@Bean
	WebMvcConfigurer corsConfigurer() {
		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				registry.addMapping("/api/**")
						.allowedOrigins("http://localhost:4200")
						.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
						.allowedHeaders("Authorization", "Content-Type")
						.allowCredentials(true);
			}
		};
	}
}
