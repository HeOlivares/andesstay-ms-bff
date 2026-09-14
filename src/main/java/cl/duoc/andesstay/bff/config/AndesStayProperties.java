package cl.duoc.andesstay.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "andesstay")
public class AndesStayProperties {

	private String catalogBaseUrl = "http://localhost:8081";
	private String reservationsBaseUrl = "http://localhost:8082";
	private final Security security = new Security();

	public String getCatalogBaseUrl() {
		return catalogBaseUrl;
	}

	public void setCatalogBaseUrl(String catalogBaseUrl) {
		this.catalogBaseUrl = catalogBaseUrl;
	}

	public String getReservationsBaseUrl() {
		return reservationsBaseUrl;
	}

	public void setReservationsBaseUrl(String reservationsBaseUrl) {
		this.reservationsBaseUrl = reservationsBaseUrl;
	}

	public Security getSecurity() {
		return security;
	}

	public static class Security {
		/** azure (default demo) | local (HMAC offline) */
		private String mode = "azure";
		private final Jwt jwt = new Jwt();

		public String getMode() {
			return mode;
		}

		public void setMode(String mode) {
			this.mode = mode;
		}

		public Jwt getJwt() {
			return jwt;
		}
	}

	public static class Jwt {
		private String localSecret = "andesstay-local-dev-secret-change-me-32b";
		private String audience = "api://4cd6df9a-e2f7-4024-aea6-dd67c49709bc";
		private String issuerUri =
				"https://login.microsoftonline.com/cb0b9f53-0ba7-4f09-8da2-c2f5ab4b73ee/v2.0";

		public String getLocalSecret() {
			return localSecret;
		}

		public void setLocalSecret(String localSecret) {
			this.localSecret = localSecret;
		}

		public String getAudience() {
			return audience;
		}

		public void setAudience(String audience) {
			this.audience = audience;
		}

		public String getIssuerUri() {
			return issuerUri;
		}

		public void setIssuerUri(String issuerUri) {
			this.issuerUri = issuerUri;
		}
	}
}
