package team.projectpulse.student;

import team.projectpulse.instructor.Instructor;
import team.projectpulse.section.Section;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.system.UserUtils;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

/**
 * Answers the questions a route asks about one named student: is that student the caller herself
 * ({@code isStudentSelf}), and is the caller an instructor of that student's course section
 * ({@code isCurrentUserInstructorOfStudentSection}).
 *
 * <p>Every check fails closed: an id that names no row, or a link not yet established, is denied rather than
 * throwing. These methods run inside an {@code AuthorizationManager}, upstream of the {@code DispatcherServlet},
 * so an exception thrown here escapes the filter chain as a 500 that also tells the caller which ids exist.
 */
@Service
@Transactional
public class StudentSecurityService {

    private final StudentRepository studentRepository;
    private final SectionRepository sectionRepository;
    private final UserUtils userUtils;


    public StudentSecurityService(StudentRepository studentRepository, SectionRepository sectionRepository, UserUtils userUtils) {
        this.studentRepository = studentRepository;
        this.sectionRepository = sectionRepository;
        this.userUtils = userUtils;
    }

    public boolean isStudentSelf(Integer studentId) {
        Integer studentIdFromJwt = this.userUtils.getUserId();
        return studentId.equals(studentIdFromJwt);
    }

    /**
     * Check if the user is the instructor of the section that the student is in
     *
     * @param studentId the id of the student we need to access, this is NOT the current user's Id
     * @return
     */
    public boolean isCurrentUserInstructorOfStudentSection(Integer studentId) {
        Integer instructorIdFromJwt = this.userUtils.getUserId();
        Student student = this.studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            return false;
        }
        return student.getSection().getInstructors().stream()
                .anyMatch(instructor -> instructor.getId().equals(instructorIdFromJwt));
    }

    /**
     * Whether the caller may change this student's lifecycle state (BR-student-lifecycle): she is an instructor
     * of the student's course section, or the course admin of its course. A role alone, {@code ROLE_admin}
     * included, grants nothing. An unknown id is {@code false}, so the route answers 403 (UC-STU-deactivate-student 6a).
     *
     * <p>Kept separate from {@link #isCurrentUserInstructorOfStudentSection}, which also guards private
     * peer-evaluation details and must not start admitting the course admin.
     */
    public boolean canManageStudentLifecycle(Integer studentId) {
        Student student = this.studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            return false;
        }
        return canManageSection(student.getSection(), this.userUtils.getUserId());
    }

    /**
     * Whether the caller may manage the lifecycle of the students in this course section, by the same rule as
     * {@link #canManageStudentLifecycle}. Asked once per search, so each row's {@code canDeactivate} can be set
     * without a lookup per student (UC-STU-deactivate-student 3b1).
     */
    public boolean canManageStudentsOfSection(Integer sectionId) {
        if (sectionId == null) {
            return false;
        }
        Section section = this.sectionRepository.findById(sectionId).orElse(null);
        if (section == null) {
            return false;
        }
        return canManageSection(section, this.userUtils.getUserId());
    }

    private static boolean canManageSection(Section section, Integer callerId) {
        if (section.getInstructors().stream().anyMatch(instructor -> instructor.getId().equals(callerId))) {
            return true;
        }
        Instructor courseAdmin = section.getCourse().getCourseAdmin();
        return courseAdmin != null && courseAdmin.getId().equals(callerId);
    }

}
