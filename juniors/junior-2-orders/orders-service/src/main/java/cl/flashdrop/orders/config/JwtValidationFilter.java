package cl.flashdrop.orders.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtValidationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtValidationFilter.class);

    private final RestClient authServiceClient;
    private final String authServiceUrl;

    public JwtValidationFilter(@Value("${auth.service.url}") String authServiceUrl) {
        this.authServiceUrl = authServiceUrl;
        this.authServiceClient = RestClient.create();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String header = req.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            try {
                ResponseEntity<Void> validation = authServiceClient.get()
                    .uri(authServiceUrl + "/auth/validate")
                    .header("Authorization", header)
                    .retrieve()
                    .toBodilessEntity();

                if (validation.getStatusCode().is2xxSuccessful()) {
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(token, null, new ArrayList<>());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }

            } catch (RestClientException e) {
                logger.warn("Auth service validation failed: {}", e.getMessage());
                res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                return;
            }
        }

        chain.doFilter(req, res);
    }
}
