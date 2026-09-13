package cl.duoc.andesstay.bff.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Maps Azure AD App Roles ({@code roles}) and optional {@code role} claim to ROLE_* authorities.
 */
@Component
public class AzureAdJwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		Collection<GrantedAuthority> authorities = extractRoles(jwt).stream()
				.map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
				.map(SimpleGrantedAuthority::new)
				.collect(Collectors.toSet());
		String principal = jwt.getClaimAsString("preferred_username");
		if (principal == null || principal.isBlank()) {
			principal = jwt.getSubject();
		}
		return new JwtAuthenticationToken(jwt, authorities, principal);
	}

	private List<String> extractRoles(Jwt jwt) {
		List<String> roles = new ArrayList<>();
		Object rolesClaim = jwt.getClaim("roles");
		if (rolesClaim instanceof Collection<?> collection) {
			collection.forEach(item -> {
				if (item != null) {
					roles.add(normalize(item.toString()));
				}
			});
		}
		String single = jwt.getClaimAsString("role");
		if (single != null && !single.isBlank()) {
			roles.add(normalize(single));
		}
		Object realmAccess = jwt.getClaim("realm_access");
		if (realmAccess instanceof Map<?, ?> map) {
			Object nested = map.get("roles");
			if (nested instanceof Collection<?> collection) {
				collection.forEach(item -> {
					if (item != null) {
						roles.add(normalize(item.toString()));
					}
				});
			}
		}
		return roles;
	}

	private String normalize(String role) {
		String value = role.trim();
		if (value.regionMatches(true, 0, "ROLE_", 0, 5)) {
			value = value.substring(5);
		}
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "admin", "administrador" -> "Admin";
			case "operador", "operator" -> "Operador";
			case "cliente", "client", "customer" -> "Cliente";
			case "auditor" -> "Auditor";
			default -> Character.toUpperCase(value.charAt(0)) + value.substring(1);
		};
	}
}
