package cl.duoc.andesstay.bff.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.andesstay.bff.proxy.DownstreamProxyService;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
		"andesstay.security.mode=local",
		"andesstay.security.jwt.audience=api://andesstay-test",
		"andesstay.security.jwt.local-secret=andesstay-local-dev-secret-change-me-32b"
})
@AutoConfigureMockMvc
class RoleAccessTest {

	private static final String SECRET = "andesstay-local-dev-secret-change-me-32b";
	private static final String AUDIENCE = "api://andesstay-test";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DownstreamProxyService proxyService;

	@BeforeEach
	void downstreamIsUp() {
		when(proxyService.forwardCatalog(any(HttpServletRequest.class), nullable(byte[].class)))
				.thenReturn(ResponseEntity.ok(new byte[0]));
		when(proxyService.forwardReservations(any(HttpServletRequest.class), nullable(byte[].class)))
				.thenReturn(ResponseEntity.ok(new byte[0]));
		when(proxyService.forwardAudit(any(HttpServletRequest.class), nullable(byte[].class)))
				.thenReturn(ResponseEntity.ok(new byte[0]));
	}

	@ParameterizedTest
	@CsvSource({
			"Admin,200",
			"Operador,200",
			"Cliente,403",
			"Auditor,403"
	})
	void catalogRead(String role, int expected) throws Exception {
		perform(get("/api/catalog/units"), role, expected);
	}

	@ParameterizedTest
	@CsvSource({
			"Admin,200",
			"Operador,403",
			"Cliente,403",
			"Auditor,403"
	})
	void catalogWrite(String role, int expected) throws Exception {
		perform(post("/api/catalog/units").contentType(MediaType.APPLICATION_JSON).content("{}"), role, expected);
	}

	@ParameterizedTest
	@CsvSource({
			"Admin,200",
			"Operador,200",
			"Cliente,200",
			"Auditor,403"
	})
	void listReservations(String role, int expected) throws Exception {
		perform(get("/api/reservations"), role, expected);
	}

	@ParameterizedTest
	@CsvSource({
			"Admin,200",
			"Operador,200",
			"Cliente,200",
			"Auditor,403"
	})
	void createReservation(String role, int expected) throws Exception {
		perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content("{}"), role, expected);
	}

	@ParameterizedTest
	@CsvSource({
			"Admin,200",
			"Operador,200",
			"Cliente,403",
			"Auditor,403"
	})
	void changeReservationStatus(String role, int expected) throws Exception {
		perform(put("/api/reservations/1/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMADA\"}"), role, expected);
	}

	@Test
	void auditRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/audit/timeline")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/audit/timeline").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@CsvSource({
			"Admin,200",
			"Operador,403",
			"Cliente,403",
			"Auditor,200"
	})
	void readAudit(String role, int expected) throws Exception {
		perform(get("/api/audit/timeline"), role, expected);
	}

	@ParameterizedTest
	@CsvSource({
			"Admin,403",
			"Operador,403",
			"Cliente,403",
			"Auditor,403"
	})
	void writeAuditIsDenied(String role, int expected) throws Exception {
		perform(post("/api/audit/timeline").contentType(MediaType.APPLICATION_JSON).content("{}"), role, expected);
		perform(put("/api/audit/timeline/1").contentType(MediaType.APPLICATION_JSON).content("{}"), role, expected);
		perform(patch("/api/audit/timeline/1").contentType(MediaType.APPLICATION_JSON).content("{}"), role, expected);
		perform(delete("/api/audit/timeline/1"), role, expected);
	}

	private void perform(MockHttpServletRequestBuilder request, String role, int expected) throws Exception {
		mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(role)))
				.andExpect(status().is(expected));
	}

	private static String token(String role) throws Exception {
		Instant now = Instant.now();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject("user-" + role.toLowerCase())
				.issuer("andesstay-local")
				.audience(AUDIENCE)
				.claim("preferred_username", role.toLowerCase() + "@andesstay.local")
				.claim("roles", List.of(role))
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
