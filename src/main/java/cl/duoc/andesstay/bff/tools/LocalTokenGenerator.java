package cl.duoc.andesstay.bff.tools;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Generates local HMAC JWTs for demo (SECURITY_MODE=local).
 * Usage: mvn -q exec:java -Dexec.mainClass=cl.duoc.andesstay.bff.tools.LocalTokenGenerator -Dexec.args="Admin"
 */
public final class LocalTokenGenerator {

	private LocalTokenGenerator() {
	}

	public static void main(String[] args) throws Exception {
		String role = args.length > 0 ? args[0] : "Admin";
		String secret = System.getenv().getOrDefault(
				"JWT_LOCAL_SECRET",
				"andesstay-local-dev-secret-change-me-32b"
		);
		String audience = System.getenv().getOrDefault("AZURE_AUDIENCE", "api://andesstay-api");

		Instant now = Instant.now();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject("local-user-" + role.toLowerCase())
				.issuer("andesstay-local")
				.audience(audience)
				.claim("preferred_username", role.toLowerCase() + "@andesstay.local")
				.claim("name", "Local " + role)
				.claim("roles", List.of(role))
				.issueTime(Date.from(now))
				.expirationTime(Date.from(now.plusSeconds(3600)))
				.build();

		SignedJWT jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
				claims
		);
		jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
		System.out.println(jwt.serialize());
	}
}
