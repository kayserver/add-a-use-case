package team.projectpulse.student;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.projectpulse.course.Course;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.section.Section;
import team.projectpulse.system.UserUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * BR-student-lifecycle, as checked by {@link StudentSecurityService#canManageStudentLifecycle}: an instructor of
 * the student's course section or the course admin of its course, and nobody else.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit tests for StudentSecurityService")
class StudentSecurityServiceTest {

    @Mock
    StudentRepository studentRepository;

    @Mock
    UserUtils userUtils;

    @InjectMocks
    StudentSecurityService studentSecurityService;

    Instructor sectionInstructor;

    Instructor courseAdmin;

    Instructor otherInstructor;

    Student john;


    private static Instructor instructor(int id, String name) {
        Instructor instructor = new Instructor(name, name, name, name + "@abc.edu", "123456", true, "instructor");
        instructor.setId(id);
        return instructor;
    }

    @BeforeEach
    void setUp() {
        this.sectionInstructor = instructor(2, "bill");
        this.courseAdmin = instructor(1, "bingyang");
        this.otherInstructor = instructor(3, "tim");

        Course course = new Course("Senior Design", "capstone");
        course.setCourseAdmin(this.courseAdmin); // also adds him to the course's instructors, not to the course section's

        Section section = new Section();
        section.setCourse(course);
        section.addInstructor(this.sectionInstructor);

        this.john = new Student("john", "John", "Smith", "j.smith@abc.edu", "123456", true, "student");
        this.john.setId(10);
        section.addStudent(this.john);
    }

    @Test
    @DisplayName("An instructor of the student's course section may manage her lifecycle")
    void sectionInstructorMayManage() {
        given(this.studentRepository.findById(10)).willReturn(Optional.of(this.john));
        given(this.userUtils.getUserId()).willReturn(2);

        assertThat(this.studentSecurityService.canManageStudentLifecycle(10)).isTrue();
    }

    @Test
    @DisplayName("The course admin may manage her lifecycle without being an instructor of the course section")
    void courseAdminAloneMayManage() {
        given(this.studentRepository.findById(10)).willReturn(Optional.of(this.john));
        given(this.userUtils.getUserId()).willReturn(1);

        assertThat(this.john.getSection().getInstructors()).doesNotContain(this.courseAdmin);
        assertThat(this.studentSecurityService.canManageStudentLifecycle(10)).isTrue();
    }

    @Test
    @DisplayName("An instructor of another course section may not (3b)")
    void otherInstructorMayNotManage() {
        given(this.studentRepository.findById(10)).willReturn(Optional.of(this.john));
        given(this.userUtils.getUserId()).willReturn(3);

        assertThat(this.studentSecurityService.canManageStudentLifecycle(10)).isFalse();
    }

    @Test
    @DisplayName("The student herself may not (3b)")
    void studentHerselfMayNotManage() {
        given(this.studentRepository.findById(10)).willReturn(Optional.of(this.john));
        given(this.userUtils.getUserId()).willReturn(10);

        assertThat(this.studentSecurityService.canManageStudentLifecycle(10)).isFalse();
    }

    @Test
    @DisplayName("An unknown student id is denied, not thrown (6a)")
    void unknownStudentIsDenied() {
        given(this.studentRepository.findById(999999)).willReturn(Optional.empty());

        assertThat(this.studentSecurityService.canManageStudentLifecycle(999999)).isFalse();
    }

    @Test
    @DisplayName("A course with no course admin does not throw")
    void courseWithoutAdminIsHandled() {
        Course orphan = new Course("Orphan", "no admin");
        this.john.getSection().setCourse(orphan);
        given(this.studentRepository.findById(10)).willReturn(Optional.of(this.john));
        given(this.userUtils.getUserId()).willReturn(1);

        assertThat(this.studentSecurityService.canManageStudentLifecycle(10)).isFalse();
    }

}
