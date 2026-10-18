package cl.duoc.andesstay.bff.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class JwtClaimsValidationTest {

	private static final String AUDIENCE = "api://andesstay-test";

	private static HttpServer server;
	private static RSAKey signingKey;
	private static String issuer;

	@DynamicPropertySource
	static void jwtProperties(DynamicPropertyRegistry registry) throws Exception {
		startIssuer();
		registry.add("andesstay.security.mode", () -> "azure");
		registry.add("andesstay.security.jwt.issuer-uri", () -> issuer);
		registry.add("andesstay.security.jwt.audience", () -> AUDIENCE);
	}

	@AfterAll
	static void stopIssuer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void validTokenReachesMe() throws Exception {
		String token = token(issuer, AUDIENCE, Instant.now().minusSeconds(30), Instant.now().plusSeconds(600), signingKey);
		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sub").value("user-1"));
	}

	@Test
	void foreignIssuerIsUnauthorized() throws Exception {
		String token = token(issuer + "/otro", AUDIENCE, Instant.now().minusSeconds(30), Instant.now().plusSeconds(600), signingKey);
		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void foreignAudienceIsUnauthorized() throws Exception {
		String token = token(issuer, "api://otro", Instant.now().minusSeconds(30), Instant.now().plusSeconds(600), signingKey);
		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void invalidSignatureIsUnauthorized() throws Exception {
		String token = token(issuer, AUDIENCE, Instant.now().minusSeconds(30), Instant.now().plusSeconds(600), otherKey());
		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void expiredTokenIsUnauthorized() throws Exception {
		String token = token(issuer, AUDIENCE, Instant.now().minusSeconds(3600), Instant.now().minusSeconds(120), signingKey);
		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	private static void startIssuer() throws Exception {
		if (server != null) {
			return;
		}
		signingKey = rsa("test-key");
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		int port = server.getAddress().getPort();
		issuer = "http://127.0.0.1:" + port;

		server.createContext("/.well-known/openid-configuration", exchange -> {
			byte[] body = ("{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/jwks\"}")
					.getBytes(StandardCharsets.UTF_8);
			writeJson(exchange, body);
		});
		server.createContext("/jwks", exchange -> {
			byte[] body = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
			writeJson(exchange, body);
		});
		server.start();
	}

	private static void writeJson(com.sun.net.httpserver.HttpExchange exchange, byte[] body) throws java.io.IOException {
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

	private static String token(String tokenIssuer, String audience, Instant issuedAt, Instant expiresAt, RSAKey key)
			throws Exception {
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject("user-1")
				.issuer(tokenIssuer)
				.audience(audience)
				.claim("preferred_username", "cliente@andesstay.local")
				.claim("roles", List.of("Cliente"))
				.issueTime(Date.from(issuedAt))
				.expirationTime(Date.from(expiresAt))
				.build();
		SignedJWT jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(),
				claims
		);
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}

	private static RSAKey rsa(String keyId) throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair pair = generator.generateKeyPair();
		return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
				.privateKey((RSAPrivateKey) pair.getPrivate())
				.keyID(keyId)
				.build();
	}

	private static RSAKey otherKey() throws Exception {
		return rsa("test-key");
	}
}
