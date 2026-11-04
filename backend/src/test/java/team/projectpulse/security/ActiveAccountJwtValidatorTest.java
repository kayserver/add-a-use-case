package team.projectpulse.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import team.projectpulse.user.UserRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * FR-SEC-active-account: the per-request check that a bearer token's account is still active. It reads the
 * shared user table, so the same check covers students and instructors.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit tests for ActiveAccountJwtValidator")
class ActiveAccountJwtValidatorTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    ActiveAccountJwtValidator validator;


    private static Jwt jwtWithUserId(Object userId) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("someone")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (userId != null) {
            builder.claim(ActiveAccountJwtValidator.USER_ID_CLAIM, userId);
        }
        return builder.build();
    }

    @Test
    @DisplayName("An enabled account passes")
    void enabledAccountPasses() {
        given(this.userRepository.existsByIdAndEnabledTrue(5)).willReturn(true);

        OAuth2TokenValidatorResult result = this.validator.validate(jwtWithUserId(5L));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("A disabled account, student or instructor, fails as deactivated")
    void disabledAccountFails() {
        given(this.userRepository.existsByIdAndEnabledTrue(5)).willReturn(false);

        OAuth2TokenValidatorResult result = this.validator.validate(jwtWithUserId(5L));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN);
            assertThat(error.getDescription()).isEqualTo("This account is deactivated.");
        });
    }

    @Test
    @DisplayName("An account that no longer exists fails")
    void missingAccountFails() {
        given(this.userRepository.existsByIdAndEnabledTrue(999999)).willReturn(false);

        assertThat(this.validator.validate(jwtWithUserId(999999L)).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("A token with no userId claim fails without a lookup")
    void missingClaimFails() {
        OAuth2TokenValidatorResult result = this.validator.validate(jwtWithUserId(null));

        assertThat(result.hasErrors()).isTrue();
        verifyNoInteractions(this.userRepository);
    }

    @Test
    @DisplayName("A userId claim that is not a whole number, or does not fit an int, fails without a lookup")
    void malformedClaimFails() {
        assertThat(this.validator.validate(jwtWithUserId("5")).hasErrors()).isTrue();
        assertThat(this.validator.validate(jwtWithUserId(5.5)).hasErrors()).isTrue();
        assertThat(this.validator.validate(jwtWithUserId(Long.MAX_VALUE)).hasErrors()).isTrue();
        verifyNoInteractions(this.userRepository);
    }

}
