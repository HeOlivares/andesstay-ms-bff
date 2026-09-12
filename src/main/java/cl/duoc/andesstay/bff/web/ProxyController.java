package cl.duoc.andesstay.bff.web;

import cl.duoc.andesstay.bff.proxy.DownstreamProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Proxy", description = "Orquestación hacia microservicios")
@SecurityRequirement(name = "bearer-jwt")
public class ProxyController {

	private final DownstreamProxyService proxyService;

	public ProxyController(DownstreamProxyService proxyService) {
		this.proxyService = proxyService;
	}

	@RequestMapping(
			value = "/api/catalog/**",
			method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE}
	)
	@Operation(summary = "Proxy hacia andesstay-ms-catalog")
	public ResponseEntity<byte[]> catalog(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
		return proxyService.forwardCatalog(request, body);
	}

	@RequestMapping(
			value = "/api/reservations/**",
			method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE}
	)
	@Operation(summary = "Proxy hacia andesstay-ms-reservations (502 si no está disponible)")
	public ResponseEntity<byte[]> reservations(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
		return proxyService.forwardReservations(request, body);
	}
}
