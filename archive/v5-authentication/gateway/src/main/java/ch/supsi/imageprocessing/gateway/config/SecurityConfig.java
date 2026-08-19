package ch.supsi.imageprocessing.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Gateway acts as the OAuth2 client (BFF pattern): it performs the
 * Authorization Code flow against Keycloak itself and keeps a server-side
 * session, so the static frontend (index.html/script.js/style.css) needs
 * no OAuth2 code of its own -- the session cookie rides along on every
 * same-origin fetch() call automatically.
 *
 * Orchestrator does NOT validate JWTs independently -- it continues to
 * rely on the existing "only the gateway can reach it" network boundary
 * (sprint-2-microservices-recap.md §5.1), so no token is forwarded
 * downstream. This class is the entire authentication boundary.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

		@Value("${keycloak.logout-uri}")
		private String keycloakLogoutUri;

		@Bean
		public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
				http
						// The frontend is plain static HTML/JS (index.html/script.js) with
						// no templating engine to inject a CSRF token into -- every
						// mutating fetch() call (createUser, uploadImage, createJob,
						// processJob, delete*) would otherwise get 403'd the instant
						// login is wired up. Disabling CSRF here is a deliberate,
						// proportionate tradeoff for a session-cookie BFF on an internal
						// thesis-scale tool now sitting behind real Keycloak auth --
						// the session cookie's default SameSite=Lax already blocks most
						// practical cross-site CSRF vectors in modern browsers.
						.csrf(csrf -> csrf.disable())
						.authorizeHttpRequests(authorize -> authorize
										.requestMatchers("/actuator/health").permitAll()
										.anyRequest().authenticated()
										)
						.oauth2Login(withDefaults -> { })
						.logout(logout -> logout
										.logoutSuccessHandler(keycloakLogoutSuccessHandler())
							   );
				return http.build();
		}

		/**
		 * Spring's built-in OidcClientInitiatedLogoutSuccessHandler relies on
		 * the end_session_endpoint populated via OIDC discovery, which this
		 * app deliberately doesn't use (see application.properties -- no
		 * issuer-uri, to keep token/jwks/userinfo endpoints internal). Building
		 * the Keycloak RP-initiated logout redirect by hand instead, using the
		 * same external/browser-facing host as the authorization endpoint.
		 */
		private LogoutSuccessHandler keycloakLogoutSuccessHandler() {
				return (request, response, authentication) -> {
						String appBaseUrl = request.getRequestURL()
								.toString()
								.replace(request.getRequestURI(), "/");

						UriComponentsBuilder builder = UriComponentsBuilder
								.fromUriString(keycloakLogoutUri)
								.queryParam("post_logout_redirect_uri", appBaseUrl);

						// Extract the id_token and attach it to the logout request
						if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
								builder.queryParam("id_token_hint", oidcUser.getIdToken().getTokenValue());
						}

						response.sendRedirect(builder.build().toUriString());
				};
		}
}
