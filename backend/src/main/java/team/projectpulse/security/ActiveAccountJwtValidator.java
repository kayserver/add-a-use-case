package team.projectpulse.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import team.projectpulse.user.UserRepository;

/**
 * Refuses a bearer token whose account is no longer active (FR-SEC-active-account).
 *
 * <p>A JWT stays valid for its whole lifetime once issued, so without this check a student or instructor who is
 * deactivated while logged in would keep access until the token expired. This validator runs on every
 * bearer-token request, beside the default signature and expiry validators, and looks the account up by the
 * token's {@code userId} claim. A missing, malformed, or out-of-range claim, or an account that is missing or not
 * enabled, fails validation, which Spring turns into an {@code InvalidBearerTokenException} and a 401.
 *
 * <p>The lookup is deliberately not cached: any cache lifetime would reopen the window the FR closes.
 */
@Component
public class ActiveAccountJwtValidator implements OAuth2TokenValidator<Jwt> {

    static final String USER_ID_CLAIM = "userId";

    private static final OAuth2Error ACCOUNT_DEACTIVATED =
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "This account is deactivated.", null);

    private static final OAuth2Error MALFORMED_USER_ID =
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The token does not name a valid account.", null);

    private final UserRepository userRepository;


    public ActiveAccountJwtValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Integer userId = userIdOf(jwt.getClaim(USER_ID_CLAIM));
        if (userId == null) {
            return OAuth2TokenValidatorResult.failure(MALFORMED_USER_ID);
        }
        if (!this.userRepository.existsByIdAndEnabledTrue(userId)) {
            return OAuth2TokenValidatorResult.failure(ACCOUNT_DEACTIVATED);
        }
        return OAuth2TokenValidatorResult.success();
    }

    /**
     * The claim is written as an {@code Integer} by {@link JwtProvider} and comes back from the JSON as a
     * {@code Long}; anything else, or a value that does not fit an {@code int}, is treated as malformed.
     */
    private static Integer userIdOf(Object claim) {
        if (!(claim instanceof Long || claim instanceof Integer)) {
            return null;
        }
        try {
            return Math.toIntExact(((Number) claim).longValue());
        } catch (ArithmeticException ex) {
            return null;
        }
    }

}
