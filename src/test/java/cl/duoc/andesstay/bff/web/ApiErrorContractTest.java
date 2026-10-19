package cl.duoc.andesstay.bff.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class ApiErrorContractTest {

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
	void missingTokenIsUnauthorized() throws Exception {
		expectUnauthorized(mockMvc.perform(get("/api/me")), "/api/me", null);
	}

	@Test
	void malformedTokenIsUnauthorized() throws Exception {
		expectUnauthorized(mockMvc.perform(bearer("no-es-un-jwt")), "/api/me", "no-es-un-jwt");
	}

	@Test
	void expiredTokenIsUnauthorized() throws Exception {
		String token = token(issuer, AUDIENCE, Instant.now().minusSeconds(3600), Instant.now().minusSeconds(120), signingKey, "Cliente");
		expectUnauthorized(mockMvc.perform(bearer(token)), "/api/me", token);
	}

	@Test
	void invalidSignatureIsUnauthorized() throws Exception {
		String token = token(issuer, AUDIENCE, Instant.now().minusSeconds(30), Instant.now().plusSeconds(600), otherKey(), "Cliente");
		expectUnauthorized(mockMvc.perform(bearer(token)), "/api/me", token);
	}

	@Test
	void foreignIssuerIsUnauthorized() throws Exception {
		String token = token(issuer + "/otro", AUDIENCE, Instant.now().minusSeconds(30), Instant.now().plusSeconds(600), signingKey, "Cliente");
		expectUnauthorized(mockMvc.perform(bearer(token)), "/api/me", token);
	}

	@Test
	void foreignAudienceIsUnauthorized() throws Exception {
		String token = token(issuer, "api://otro", Instant.now().minusSeconds(30), Instant.now().plusSeconds(600), signingKey, "Cliente");
		expectUnauthorized(mockMvc.perform(bearer(token)), "/api/me", token);
	}

	@Test
	void unauthorizedRoleIsForbidden() throws Exception {
		String token = token(issuer, AUDIENCE, Instant.now().minusSeconds(30), Instant.now().plusSeconds(600), signingKey, "Operador");
		MvcResult result = expectError(
				mockMvc.perform(post("/api/catalog/units")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)),
				403,
				"Forbidden",
				"/api/catalog/units"
		).andReturn();
		assertFalse(result.getResponse().getContentAsString().contains(token));
	}

	private ResultActions expectUnauthorized(ResultActions result, String path, String secret) throws Exception {
		ResultActions checked = expectError(result, 401, "Unauthorized", path);
		if (secret != null) {
			assertFalse(checked.andReturn().getResponse().getContentAsString().contains(secret));
		}
		return checked;
	}

	private ResultActions expectError(ResultActions result, int status, String error, String path) throws Exception {
		return result.andExpect(status().is(status))
				.andExpect(jsonPath("$.status").value(status))
				.andExpect(jsonPath("$.error").value(error))
				.andExpect(jsonPath("$.path").value(path))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.message").isString());
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder bearer(String token) {
		return get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private static void startIssuer() throws Exception {
		if (server != null) {
			return;
		}
		signingKey = rsa("andesstay-test");
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		issuer = "http://127.0.0.1:" + server.getAddress().getPort();
		server.createContext("/.well-known/openid-configuration", exchange -> writeJson(exchange,
				("{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/jwks\"}").getBytes(StandardCharsets.UTF_8)));
		server.createContext("/jwks", exchange -> writeJson(exchange,
				new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8)));
		server.start();
	}

	private static void writeJson(HttpExchange exchange, byte[] body) throws IOException {
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

	private static String token(String tokenIssuer, String audience, Instant issuedAt, Instant expiresAt, RSAKey key, String role)
			throws Exception {
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject("user-1")
				.issuer(tokenIssuer)
				.audience(audience)
				.claim("preferred_username", role.toLowerCase() + "@andesstay.local")
				.claim("roles", List.of(role))
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
		return rsa("andesstay-test");
	}
}
