package team.projectpulse.student.converter;

import team.projectpulse.student.Student;
import team.projectpulse.student.dto.StudentDto;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class StudentToStudentDtoConverter implements Converter<Student, StudentDto> {

    /**
     * The DTO every caller may see: no deactivation date, no {@code canDeactivate}. Used wherever a student can read
     * the result (her teammates, login), so a student never learns a teammate's deactivation date.
     */
    @Override
    public StudentDto convert(Student student) {
        return toDto(student, null, null);
    }

    /**
     * The instructor-facing DTO: carries her deactivation date and, on search rows, whether the caller is offered
     * Deactivate (UC-STU-deactivate-student).
     */
    public StudentDto convertWithLifecycle(Student student, Boolean canDeactivate) {
        return toDto(student, student.getDeactivatedOn(), canDeactivate);
    }

    private static StudentDto toDto(Student student, LocalDate deactivatedOn, Boolean canDeactivate) {
        // We are not setting password in DTO.
        return new StudentDto(
                student.getId(),
                student.getUsername(),
                student.getFirstName(),
                student.getLastName(),
                student.getEmail(),
                student.isEnabled(),
                student.getRoles(),
                student.getSection().getSectionId(),
                student.getSection().getSectionName(),
                student.getTeam() == null ? null : student.getTeam().getTeamId(),
                student.getTeam() == null ? null : student.getTeam().getTeamName(),
                deactivatedOn,
                canDeactivate
        );
    }

}
