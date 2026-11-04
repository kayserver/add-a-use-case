package team.projectpulse.student.dto;

import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;

/**
 * @param deactivatedOn the date she was deactivated, or null while active. Filled only on the instructor-facing
 *                      endpoints (search rows, the single-student GET); null wherever teammates can read it.
 * @param canDeactivate whether the caller is offered Deactivate for her (UC-STU-deactivate-student 3a1, 3b1). Set
 *                      only on search rows; null from every other endpoint. Ignored in a request body.
 */
public record StudentDto(Integer id,
                         @NotEmpty(message = "username is required.")
                         String username,
                         @NotEmpty(message = "first rubricName is required.")
                         String firstName,
                         @NotEmpty(message = "last rubricName is required.")
                         String lastName,
                         @NotEmpty(message = "email is required.")
                         String email,
                         boolean enabled,
                         String roles,
                         Integer sectionId,
                         String sectionName,
                         Integer teamId,
                         String teamName,
                         LocalDate deactivatedOn,
                         Boolean canDeactivate) {
}
