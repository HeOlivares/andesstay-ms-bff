package cl.duoc.andesstay.bff.config;

import cl.duoc.andesstay.bff.security.AzureAdJwtAuthConverter;
import cl.duoc.andesstay.bff.web.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AndesStayProperties.class)
public class SecurityConfig {

	private final AndesStayProperties properties;
	private final AzureAdJwtAuthConverter jwtAuthConverter;
	private final ObjectMapper objectMapper;

	public SecurityConfig(
			AndesStayProperties properties,
			AzureAdJwtAuthConverter jwtAuthConverter,
			ObjectMapper objectMapper) {
		this.properties = properties;
		this.jwtAuthConverter = jwtAuthConverter;
		this.objectMapper = objectMapper;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(
								"/api/health",
								"/actuator/health",
								"/actuator/health/**",
								"/v3/api-docs/**",
								"/api-docs/**",
								"/swagger-ui/**",
								"/swagger-ui.html"
						).permitAll()
						.requestMatchers(HttpMethod.GET, "/api/catalog/**")
						.hasAnyRole("Admin", "Operador", "Auditor")
						.requestMatchers(HttpMethod.POST, "/api/catalog/**").hasRole("Admin")
						.requestMatchers(HttpMethod.PUT, "/api/catalog/**").hasRole("Admin")
						.requestMatchers(HttpMethod.PATCH, "/api/catalog/**").hasRole("Admin")
						.requestMatchers(HttpMethod.DELETE, "/api/catalog/**").hasRole("Admin")
						.requestMatchers("/api/reservations/**")
						.hasAnyRole("Admin", "Operador", "Cliente", "Auditor")
						.requestMatchers("/api/me").authenticated()
						.anyRequest().authenticated()
				)
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
						.authenticationEntryPoint(authenticationEntryPoint())
						.accessDeniedHandler(accessDeniedHandler())
				)
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(authenticationEntryPoint())
						.accessDeniedHandler(accessDeniedHandler())
				);
		return http.build();
	}

	@Bean
	@ConditionalOnProperty(name = "andesstay.security.mode", havingValue = "local")
	JwtDecoder localJwtDecoder() {
		byte[] secret = properties.getSecurity().getJwt().getLocalSecret().getBytes(StandardCharsets.UTF_8);
		SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		OAuth2TokenValidator<Jwt> withAudience = audienceValidator(properties.getSecurity().getJwt().getAudience());
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), withAudience));
		return decoder;
	}

	@Bean
	@ConditionalOnProperty(name = "andesstay.security.mode", havingValue = "azure", matchIfMissing = true)
	JwtDecoder azureJwtDecoder() {
		String issuer = properties.getSecurity().getJwt().getIssuerUri();
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
		OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
		OAuth2TokenValidator<Jwt> withAudience = audienceValidator(properties.getSecurity().getJwt().getAudience());
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
		return decoder;
	}

	/**
	 * MSAL access tokens often use Client ID as {@code aud} without the {@code api://} prefix.
	 * Accept both App ID URI and bare Client ID forms.
	 */
	private OAuth2TokenValidator<Jwt> audienceValidator(String expectedAudience) {
		Set<String> accepted = audienceAliases(expectedAudience);
		return token -> {
			if (token.getAudience() != null) {
				for (String aud : token.getAudience()) {
					if (accepted.contains(aud)) {
						return OAuth2TokenValidatorResult.success();
					}
				}
			}
			OAuth2Error error = new OAuth2Error("invalid_token", "Invalid audience", null);
			return OAuth2TokenValidatorResult.failure(error);
		};
	}

	static Set<String> audienceAliases(String configured) {
		Set<String> accepted = new LinkedHashSet<>();
		if (configured == null || configured.isBlank()) {
			return accepted;
		}
		String value = configured.trim();
		accepted.add(value);
		if (value.startsWith("api://")) {
			accepted.add(value.substring("api://".length()));
		} else {
			accepted.add("api://" + value);
		}
		return accepted;
	}

	private AuthenticationEntryPoint authenticationEntryPoint() {
		return (request, response, authException) -> {
			response.setStatus(HttpStatus.UNAUTHORIZED.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			ApiError body = new ApiError(
					Instant.now(),
					HttpStatus.UNAUTHORIZED.value(),
					"Unauthorized",
					authException.getMessage() != null ? authException.getMessage() : "Authentication required",
					request.getRequestURI()
			);
			objectMapper.writeValue(response.getOutputStream(), body);
		};
	}

	private AccessDeniedHandler accessDeniedHandler() {
		return (request, response, accessDeniedException) -> {
			response.setStatus(HttpStatus.FORBIDDEN.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			ApiError body = new ApiError(
					Instant.now(),
					HttpStatus.FORBIDDEN.value(),
					"Forbidden",
					accessDeniedException.getMessage() != null ? accessDeniedException.getMessage() : "Access denied",
					request.getRequestURI()
			);
			objectMapper.writeValue(response.getOutputStream(), body);
		};
	}
}
