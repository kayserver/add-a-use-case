package team.projectpulse.system.exception;

/**
 * A student lifecycle action requested from a state that does not allow it, such as deactivating a student who is
 * already deactivated (UC-STU-deactivate-student 3a2). Mapped to 409 by {@link ExceptionHandlerAdvice}.
 */
public class StudentStatusConflictException extends RuntimeException {

    public StudentStatusConflictException(String message) {
        super(message);
    }

}
