package cl.duoc.andesstay.bff.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "UP", "service", "andesstay-ms-bff");
	}

	@GetMapping("/me")
	public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("sub", jwt.getSubject());
		body.put("preferred_username", jwt.getClaimAsString("preferred_username"));
		body.put("name", jwt.getClaimAsString("name"));
		body.put("roles", jwt.getClaim("roles"));
		body.put("aud", jwt.getAudience());
		body.put("iss", jwt.getClaimAsString("iss"));
		body.put("exp", jwt.getExpiresAt() != null ? jwt.getExpiresAt().toString() : null);
		body.put("authorities", SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.collect(Collectors.toList()));

		Map<String, Object> safeClaims = new LinkedHashMap<>();
		jwt.getClaims().forEach((key, value) -> safeClaims.put(key, stringifyClaim(value)));
		body.put("claims", safeClaims);
		return body;
	}

	private Object stringifyClaim(Object value) {
		if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
			return value;
		}
		if (value instanceof Iterable<?> iterable) {
			return iterable;
		}
		return String.valueOf(value);
	}
}
