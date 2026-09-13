package cl.duoc.andesstay.bff.proxy;

import cl.duoc.andesstay.bff.config.AndesStayProperties;
import cl.duoc.andesstay.bff.web.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Enumeration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class DownstreamProxyService {

	private final WebClient webClient;
	private final AndesStayProperties properties;
	private final ObjectMapper objectMapper;

	public DownstreamProxyService(
			WebClient.Builder webClientBuilder,
			AndesStayProperties properties,
			ObjectMapper objectMapper) {
		this.webClient = webClientBuilder.build();
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	public ResponseEntity<byte[]> forwardCatalog(HttpServletRequest request, byte[] body) {
		return forward(properties.getCatalogBaseUrl(), request, body, "catalog");
	}

	public ResponseEntity<byte[]> forwardReservations(HttpServletRequest request, byte[] body) {
		return forward(properties.getReservationsBaseUrl(), request, body, "reservations");
	}

	private ResponseEntity<byte[]> forward(
			String baseUrl,
			HttpServletRequest request,
			byte[] body,
			String serviceName) {
		String uri = baseUrl + request.getRequestURI()
				+ (request.getQueryString() != null ? "?" + request.getQueryString() : "");
		HttpMethod method = HttpMethod.valueOf(request.getMethod());
		HttpHeaders headers = copyHeaders(request);

		try {
			WebClient.RequestBodySpec spec = webClient.method(method)
					.uri(uri)
					.headers(h -> h.addAll(headers));

			ResponseEntity<byte[]> entity;
			if (body != null && body.length > 0 && supportsBody(method)) {
				entity = spec.body(BodyInserters.fromValue(body))
						.exchangeToMono(response -> response.toEntity(byte[].class))
						.block();
			} else {
				entity = spec.exchangeToMono(response -> response.toEntity(byte[].class)).block();
			}
			return sanitize(entity);
		} catch (WebClientResponseException ex) {
			HttpHeaders responseHeaders = new HttpHeaders();
			responseHeaders.putAll(ex.getHeaders());
			responseHeaders.remove(HttpHeaders.TRANSFER_ENCODING);
			responseHeaders.remove(HttpHeaders.CONTENT_LENGTH);
			return ResponseEntity.status(ex.getStatusCode())
					.headers(responseHeaders)
					.body(ex.getResponseBodyAsByteArray());
		} catch (WebClientRequestException ex) {
			return gatewayError(request, serviceName, ex.getMessage());
		} catch (Exception ex) {
			return gatewayError(request, serviceName, ex.getMessage());
		}
	}

	private ResponseEntity<byte[]> gatewayError(HttpServletRequest request, String serviceName, String detail) {
		ApiError error = new ApiError(
				Instant.now(),
				HttpStatus.BAD_GATEWAY.value(),
				"Bad Gateway",
				"Downstream service unavailable: " + serviceName + " (" + detail + ")",
				request.getRequestURI()
		);
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.contentType(MediaType.APPLICATION_JSON)
				.body(toJson(error));
	}

	private ResponseEntity<byte[]> sanitize(ResponseEntity<byte[]> entity) {
		if (entity == null) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
		}
		HttpHeaders headers = new HttpHeaders();
		headers.putAll(entity.getHeaders());
		headers.remove(HttpHeaders.TRANSFER_ENCODING);
		headers.remove(HttpHeaders.CONTENT_LENGTH);
		return ResponseEntity.status(entity.getStatusCode())
				.headers(headers)
				.body(entity.getBody());
	}

	private HttpHeaders copyHeaders(HttpServletRequest request) {
		HttpHeaders headers = new HttpHeaders();
		Enumeration<String> names = request.getHeaderNames();
		while (names.hasMoreElements()) {
			String name = names.nextElement();
			if (HttpHeaders.HOST.equalsIgnoreCase(name)
					|| HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)
					|| HttpHeaders.CONNECTION.equalsIgnoreCase(name)) {
				continue;
			}
			Enumeration<String> values = request.getHeaders(name);
			while (values.hasMoreElements()) {
				headers.add(name, values.nextElement());
			}
		}
		return headers;
	}

	private boolean supportsBody(HttpMethod method) {
		return method == HttpMethod.POST
				|| method == HttpMethod.PUT
				|| method == HttpMethod.PATCH
				|| method == HttpMethod.DELETE;
	}

	private byte[] toJson(ApiError error) {
		try {
			return objectMapper.writeValueAsBytes(error);
		} catch (Exception e) {
			return ("{\"message\":\"" + error.message() + "\"}").getBytes();
		}
	}
}
