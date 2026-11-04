package team.projectpulse.student;

import team.projectpulse.section.Section;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.system.UserUtils;
import team.projectpulse.system.WeekKeys;
import team.projectpulse.system.exception.ObjectNotFoundException;
import team.projectpulse.system.exception.StudentStatusConflictException;
import team.projectpulse.user.userinvitation.UserInvitationService;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class StudentService {

    private final StudentRepository studentRepository;
    private final SectionRepository sectionRepository;
    private final UserInvitationService userInvitationService;
    private final PasswordEncoder passwordEncoder;
    private final UserUtils userUtils;
    private final StudentSecurityService studentSecurityService;
    private final Clock clock;


    public StudentService(StudentRepository studentRepository, SectionRepository sectionRepository, UserInvitationService userInvitationService, PasswordEncoder passwordEncoder, UserUtils userUtils, StudentSecurityService studentSecurityService, Clock clock) {
        this.studentSecurityService = studentSecurityService;
        this.studentRepository = studentRepository;
        this.sectionRepository = sectionRepository;
        this.userInvitationService = userInvitationService;
        this.passwordEncoder = passwordEncoder;
        this.userUtils = userUtils;
        this.clock = clock;
    }

    public StudentSearchResult findByCriteria(Map<String, String> searchCriteria, Pageable pageable) {
        Integer sectionId = this.userUtils.getUserSectionId();

        Specification<Student> spec = Specification.unrestricted(); // Start with an unrestricted specification, matching all objects.

        if (StringUtils.hasLength(searchCriteria.get("firstName"))) {
            spec = spec.and(StudentSpecs.containsFirstName(searchCriteria.get("firstName")));
        }

        if (StringUtils.hasLength(searchCriteria.get("lastName"))) {
            spec = spec.and(StudentSpecs.containsLastName(searchCriteria.get("lastName")));
        }

        if (StringUtils.hasLength(searchCriteria.get("email"))) {
            spec = spec.and(StudentSpecs.hasEmail(searchCriteria.get("email")));
        }

        if (StringUtils.hasLength(searchCriteria.get("teamName"))) {
            spec = spec.and(StudentSpecs.hasTeamName(searchCriteria.get("teamName")));
        }

        if (StringUtils.hasLength(searchCriteria.get("teamId"))) {
            spec = spec.and(StudentSpecs.hasTeamId(searchCriteria.get("teamId")));
        }

        if (StringUtils.hasLength(searchCriteria.get("sectionName"))) {
            spec = spec.and(StudentSpecs.hasSectionName(searchCriteria.get("sectionName")));
        }

        spec = spec.and(StudentSpecs.belongsToSection(sectionId)); // Only show students that belong to the default section of the instructor

        return new StudentSearchResult(this.studentRepository.findAll(spec, pageable), sectionId);
    }

    public Student findStudentById(Integer studentId) {
        return this.studentRepository.findById(studentId)
                .orElseThrow(() -> new ObjectNotFoundException("student", studentId));
    }

    /**
     * Her team's members. With a week, leaves out any member whose deactivation week is earlier than it, so the peer
     * evaluation form shows a deactivated teammate exactly for the weeks she may be evaluated
     * (BR-deactivated-student-weeks).
     *
     * @throws IllegalArgumentException if {@code week} is given and is not a well-formed week key
     */
    public List<Student> findStudentsByTeamId(Integer teamId, String week) {
        List<Student> members = this.studentRepository.findByTeamTeamId(teamId);
        if (week == null) {
            return members;
        }
        WeekKeys.requireValid(week);
        return members.stream().filter(student -> !student.wasDeactivatedBefore(week)).toList();
    }

    public Student saveStudent(Student newStudent, Integer courseId, Integer sectionId, String registrationToken, String role) {
        // Validate user invitation
        this.userInvitationService.validateUserInvitation(newStudent.getEmail(), registrationToken, courseId, sectionId, role);

        // Password rule: At least 6 characters, contains at least one letter and one number.
        if (!newStudent.getPassword().matches("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6,}$")) {
            throw new IllegalArgumentException("Password must contain at least one letter and one number, and at least 6 characters.");
        }

        // Add the student to the section
        Section section = this.sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ObjectNotFoundException("section", sectionId));
        section.addStudent(newStudent);

        newStudent.setEnabled(true);
        newStudent.setRoles("student");
        newStudent.setPassword(this.passwordEncoder.encode(newStudent.getPassword()));

        Student savedStudent = this.studentRepository.save(newStudent);
        // Delete the user invitation after the student is saved.
        this.userInvitationService.deleteUserInvitation(newStudent.getEmail());
        return savedStudent;
    }

    /**
     * Edits her username, names, and email. Deactivation is no longer done here: it has its own confirmed action,
     * {@link #deactivateStudent} (UC-STU-deactivate-student). {@code enabled} in the update may only re-enable a
     * deactivated student, and only for a caller who may manage her lifecycle (BR-student-lifecycle); that clears
     * her deactivation date. This is the interim reactivation path until UC-STU-reactivate-student is designed.
     * An {@code enabled} of false is ignored.
     */
    public Student updateStudent(Integer studentId, Student update) {
        Student oldStudent = this.studentRepository.findById(studentId)
                .orElseThrow(() -> new ObjectNotFoundException("student", studentId));

        oldStudent.setUsername(update.getUsername());
        oldStudent.setFirstName(update.getFirstName());
        oldStudent.setLastName(update.getLastName());
        oldStudent.setEmail(update.getEmail());

        if (update.isEnabled() && !oldStudent.isEnabled() && this.studentSecurityService.canManageStudentLifecycle(studentId)) {
            oldStudent.setEnabled(true);
            oldStudent.setDeactivatedOn(null);
        }

        return this.studentRepository.save(oldStudent);
    }

    /**
     * UC-STU-deactivate-student step 6: deactivates her and records her deactivation date, keeping her team and
     * her submitted work. Her next request is then refused (FR-SEC-active-account).
     *
     * <p>The student is loaded scoped to the caller and row-locked. Nothing back means she was deleted after the
     * route rule passed, or is out of the caller's scope: a permission error, never a 404 (6a). A student who is
     * already deactivated is refused with 409 before any write, so her original date is kept (3a2).
     */
    public Student deactivateStudent(Integer studentId) {
        Student student = this.studentRepository.findManageableByIdForUpdate(studentId, this.userUtils.getUserId())
                .orElseThrow(() -> new AccessDeniedException("Not permitted to deactivate student " + studentId + "."));
        if (!student.isEnabled()) {
            throw new StudentStatusConflictException("Student is already deactivated.");
        }
        student.setEnabled(false);
        student.setDeactivatedOn(LocalDate.now(this.clock));
        return this.studentRepository.save(student);
    }

    public void deleteStudent(Integer studentId) {
        this.studentRepository.findById(studentId)
                .orElseThrow(() -> new ObjectNotFoundException("student", studentId));
        this.studentRepository.deleteById(studentId);
    }

}
