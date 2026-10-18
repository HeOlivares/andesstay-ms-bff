package cl.duoc.andesstay.bff.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
		"andesstay.security.mode=local",
		"andesstay.security.jwt.audience=api://andesstay-test",
		"andesstay.security.jwt.local-secret=andesstay-local-dev-secret-change-me-32b"
})
@AutoConfigureMockMvc
class MeEndpointTest {

	private static final String SECRET = "andesstay-local-dev-secret-change-me-32b";
	private static final String AUDIENCE = "api://andesstay-test";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void meWithoutTokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void meReturnsIdentityAndRolesWithoutTheToken() throws Exception {
		String token = token();

		MvcResult result = mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sub").value("user-1"))
				.andExpect(jsonPath("$.preferred_username").value("cliente@andesstay.local"))
				.andExpect(jsonPath("$.name").value("Local Cliente"))
				.andExpect(jsonPath("$.roles[0]").value("Cliente"))
				.andExpect(jsonPath("$.authorities[0]").value("ROLE_Cliente"))
				.andReturn();

		assertFalse(result.getResponse().getContentAsString().contains(token));
	}

	private static String token() throws Exception {
		Instant now = Instant.now();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject("user-1")
				.issuer("andesstay-local")
				.audience(AUDIENCE)
				.claim("preferred_username", "cliente@andesstay.local")
				.claim("name", "Local Cliente")
				.claim("roles", List.of("Cliente"))
				.issueTime(Date.from(now.minusSeconds(30)))
				.expirationTime(Date.from(now.plusSeconds(600)))
				.build();
		SignedJWT jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
				claims
		);
		jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
		return jwt.serialize();
	}
}
