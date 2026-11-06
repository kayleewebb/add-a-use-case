package team.projectpulse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import team.projectpulse.user.PeerEvaluationUser;
import team.projectpulse.user.UserRepository;

import java.io.IOException;

/**
 * Refuses a still-unexpired token after the account it names has been deactivated (FR-SEC-active-account).
 *
 * <p>Login already fails for a deactivated account, because {@code MyUserPrincipal.isEnabled()} is false. A token
 * issued before that stays valid for its two hours unless something checks the account on the next request. This
 * filter does that check on the resource-server chain, after the JWT has been authenticated, and answers through
 * the same entry point an expired token uses. A request with no bearer token, including login, is left alone.
 * The filter is constructed inside {@code SecurityConfiguration} and added with {@code addFilterAfter}, rather
 * than registered as a servlet {@code Filter} bean, so it does not also run outside that chain.
 */
public class ActiveAccountFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final CustomBearerTokenAuthenticationEntryPoint authenticationEntryPoint;


    public ActiveAccountFilter(UserRepository userRepository, CustomBearerTokenAuthenticationEntryPoint authenticationEntryPoint) {
        this.userRepository = userRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            filterChain.doFilter(request, response);
            return;
        }
        Integer userId = userId(jwtAuthentication.getToken());
        PeerEvaluationUser account = userId == null ? null : this.userRepository.findById(userId).orElse(null);
        if (account == null || !account.isEnabled()) {
            this.authenticationEntryPoint.commence(request, response, new InvalidBearerTokenException("The account is deactivated."));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * The {@code userId} claim is an integer when the token is built and often a long once it has been parsed.
     */
    private static Integer userId(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        if (claim instanceof Integer i) {
            return i;
        }
        if (claim instanceof Long l) {
            return l.intValue();
        }
        if (claim instanceof String s) {
            try {
                return Integer.valueOf(s);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

}
