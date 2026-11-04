package team.projectpulse.security.authorizationmanagers;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import team.projectpulse.student.StudentSecurityService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit tests for StudentLifecycleAuthorizationManager")
class StudentLifecycleAuthorizationManagerTest {

    @Mock
    StudentSecurityService studentSecurityService;

    @InjectMocks
    StudentLifecycleAuthorizationManager manager;


    private RequestAuthorizationContext contextWith(Map<String, String> variables) {
        return new RequestAuthorizationContext(mock(HttpServletRequest.class), variables);
    }

    @Test
    @DisplayName("Grants when canManageStudentLifecycle admits the bound studentId")
    void grantsWhenPermitted() {
        given(this.studentSecurityService.canManageStudentLifecycle(7)).willReturn(true);

        AuthorizationResult result = this.manager.authorize(() -> null, contextWith(Map.of("studentId", "7")));

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isTrue();
    }

    @Test
    @DisplayName("Denies when canManageStudentLifecycle refuses")
    void deniesWhenNotPermitted() {
        given(this.studentSecurityService.canManageStudentLifecycle(7)).willReturn(false);

        assertThat(this.manager.authorize(() -> null, contextWith(Map.of("studentId", "7"))).isGranted()).isFalse();
    }

    @Test
    @DisplayName("Denies without asking when no studentId was bound")
    void deniesWhenTheVariableIsAbsent() {
        assertThat(this.manager.authorize(() -> null, contextWith(Map.of())).isGranted()).isFalse();
        verifyNoInteractions(this.studentSecurityService);
    }

}
