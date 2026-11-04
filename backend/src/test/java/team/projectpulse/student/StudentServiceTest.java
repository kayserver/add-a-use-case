package team.projectpulse.student;

import org.junit.jupiter.api.AfterEach;
import team.projectpulse.section.Section;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.system.UserUtils;
import team.projectpulse.user.userinvitation.UserInvitationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import team.projectpulse.team.Team;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.security.access.AccessDeniedException;
import team.projectpulse.system.exception.StudentStatusConflictException;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    StudentRepository studentRepository;
    @Mock
    SectionRepository sectionRepository;
    @Mock
    UserInvitationService userInvitationService;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    UserUtils userUtils;
    @Mock
    StudentSecurityService studentSecurityService;
    // The dev profile's fixed clock: Sunday 2023-08-20 23:30 America/Chicago
    @Spy
    Clock clock = Clock.fixed(ZonedDateTime.of(2023, 8, 20, 23, 30, 0, 0, ZoneId.of("America/Chicago")).toInstant(), ZoneId.of("America/Chicago"));

    @InjectMocks
    private StudentService studentService;

    List<Student> students;

    @BeforeEach
    void setUp() {
        // Create students
        Student john = new Student("john", "John", "Smith", "j.smith@abc.edu", "123456", true, "student");
        Student eric = new Student("eric", "Eric", "Hudson", "e.hudson@abc.edu", "123456", true, "student");
        Student jerry = new Student("jerry", "Jerry", "Moon", "j.moon@abc.edu", "123456", true, "student");
        Student woody = new Student("woody", "Woody", "Allen", "w.allen@abc.edu", "123456", true, "student");
        Student amanda = new Student("amanda", "Amanda", "Wagner", "a.wagner@abc.edu", "123456", true, "student");
        Student cora = new Student("cora", "Cora", "Manning", "c.manning@abc.edu", "123456", true, "student");
        Student agustin = new Student("agustin", "Agustin", "Freeman", "a.freeman@abc.edu", "123456", true, "student");
        Student mavis = new Student("mavis", "Mavis", "Huber", "m.huber@abc.edu", "123456", true, "student");
        Student mary = new Student("mary", "Mary", "Vargas", "m.vargas@abc.edu", "123456", true, "student");
        Student rosendo = new Student("rosendo", "Rosendo", "Maxwell", "r.maxwell@abc.edu", "123456", true, "student");

        this.students = List.of(john, eric, jerry, woody, amanda, cora, agustin, mavis, mary, rosendo);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext(); // Clear the security context after each test to avoid interference between tests
    }

    @Test
    void testFindByCriteria() {
        // Given
        Map<String, String> searchCriteria = Map.of("firstName", "john");
        PageImpl expectedPage = new PageImpl(List.of(this.students.get(0)));

        given(this.userUtils.getUserSectionId()).willReturn(2);
        given(this.studentRepository.findAll(any(Specification.class), any(Pageable.class))).willReturn(expectedPage);
        // When
        StudentSearchResult result = this.studentService.findByCriteria(searchCriteria, PageRequest.of(0, 20));

        // Then
        assertThat(result.page().getContent().size()).isEqualTo(1);
        assertThat(result.sectionId()).isEqualTo(2);
    }

    @Test
    void testFindStudentById() {
        // Given
        Student john = this.students.get(0);
        given(this.studentRepository.findById(1)).willReturn(Optional.of(john));

        // When
        Student result = this.studentService.findStudentById(1);

        // Then
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Smith");
    }

    @Test
    void testSaveStudent() {
        // Given
        Student jan = new Student("jan", "Jan", "Mckinney", "j.mckinney@abc.edu", "Abc123456", true, "student");
        doNothing().when(this.userInvitationService).validateUserInvitation(anyString(), anyString(), anyInt(), anyInt(), anyString());
        given(this.sectionRepository.findById(1)).willReturn(Optional.of(new Section()));
        given(this.passwordEncoder.encode(anyString())).willReturn("Encoded Password");
        given(this.studentRepository.save(jan)).willReturn(jan);

        // When
        Student result = this.studentService.saveStudent(jan, 1, 1, "token", "student");

        // Then
        assertThat(result.getFirstName()).isEqualTo("Jan");
        assertThat(result.getLastName()).isEqualTo("Mckinney");
        assertThat(result.getEmail()).isEqualTo("j.mckinney@abc.edu");
        assertThat(result.getRoles()).isEqualTo("student");
        assertThat(result.getSection()).isNotNull();
        assertThat(result.isEnabled()).isEqualTo(true);
    }

    @Test
    @DisplayName("Update: edits her details; enabled false in the body is ignored, so the PUT cannot deactivate")
    void testUpdateStudentIgnoresDisable() {
        Student john = this.students.get(0);
        Student update = new Student("john", "John (updated)", "Smith (updated)", "j.smith@abc.edu (updated)", "", false, "admin student");
        given(this.studentRepository.findById(1)).willReturn(Optional.of(john));
        given(this.studentRepository.save(any(Student.class))).willReturn(john);

        Student result = this.studentService.updateStudent(1, update);

        assertThat(result.getUsername()).isEqualTo(update.getUsername());
        assertThat(result.getFirstName()).isEqualTo(update.getFirstName());
        assertThat(result.getLastName()).isEqualTo(update.getLastName());
        assertThat(result.getEmail()).isEqualTo(update.getEmail());
        assertThat(result.getRoles()).isEqualTo("student");
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getDeactivatedOn()).isNull();
        verifyNoInteractions(this.studentSecurityService);
    }

    @Test
    @DisplayName("Update: enabled true from a permitted caller re-enables a deactivated student and clears her date")
    void testUpdateStudentReEnablesForPermittedCaller() {
        Student john = this.students.get(0);
        john.setEnabled(false);
        john.setDeactivatedOn(LocalDate.of(2023, 8, 1));
        Student update = new Student("john", "John", "Smith", "j.smith@abc.edu", "", true, "student");
        given(this.studentRepository.findById(1)).willReturn(Optional.of(john));
        given(this.studentRepository.save(any(Student.class))).willReturn(john);
        given(this.studentSecurityService.canManageStudentLifecycle(1)).willReturn(true);

        Student result = this.studentService.updateStudent(1, update);

        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getDeactivatedOn()).isNull();
    }

    @Test
    @DisplayName("Update: enabled true from a caller who may not manage her lifecycle (e.g. the student) is ignored")
    void testUpdateStudentIgnoresReEnableFromUnpermittedCaller() {
        Student john = this.students.get(0);
        john.setEnabled(false);
        john.setDeactivatedOn(LocalDate.of(2023, 8, 1));
        Student update = new Student("john", "John", "Smith", "j.smith@abc.edu", "", true, "student");
        given(this.studentRepository.findById(1)).willReturn(Optional.of(john));
        given(this.studentRepository.save(any(Student.class))).willReturn(john);
        given(this.studentSecurityService.canManageStudentLifecycle(1)).willReturn(false);

        Student result = this.studentService.updateStudent(1, update);

        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getDeactivatedOn()).isEqualTo(LocalDate.of(2023, 8, 1));
    }

    @Test
    @DisplayName("UC-STU-deactivate-student main: deactivates her, records the clock's date, keeps her team")
    void testDeactivateStudentSuccess() {
        Student john = this.students.get(0);
        Team team = new Team();
        john.setTeam(team);
        given(this.userUtils.getUserId()).willReturn(2);
        given(this.studentRepository.findManageableByIdForUpdate(4, 2)).willReturn(Optional.of(john));
        given(this.studentRepository.save(john)).willReturn(john);

        Student result = this.studentService.deactivateStudent(4);

        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getDeactivatedOn()).isEqualTo(LocalDate.of(2023, 8, 20));
        assertThat(result.getTeam()).isSameAs(team);
    }

    @Test
    @DisplayName("UC-STU-deactivate-student 3a2: already deactivated is a conflict and keeps the original date")
    void testDeactivateStudentAlreadyDeactivated() {
        Student john = this.students.get(0);
        john.setEnabled(false);
        john.setDeactivatedOn(LocalDate.of(2023, 8, 1));
        given(this.userUtils.getUserId()).willReturn(2);
        given(this.studentRepository.findManageableByIdForUpdate(4, 2)).willReturn(Optional.of(john));

        assertThatThrownBy(() -> this.studentService.deactivateStudent(4))
                .isInstanceOf(StudentStatusConflictException.class)
                .hasMessage("Student is already deactivated.");
        assertThat(john.getDeactivatedOn()).isEqualTo(LocalDate.of(2023, 8, 1));
        verify(this.studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("UC-STU-deactivate-student 6a: no student in the caller's scope is a permission error, not a 404")
    void testDeactivateStudentNotInScope() {
        given(this.userUtils.getUserId()).willReturn(2);
        given(this.studentRepository.findManageableByIdForUpdate(999999, 2)).willReturn(Optional.empty());

        assertThatThrownBy(() -> this.studentService.deactivateStudent(999999))
                .isInstanceOf(AccessDeniedException.class);
        verify(this.studentRepository, never()).save(any());
    }

}
