package team.projectpulse.student;

import jakarta.persistence.EntityManager;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.system.StatusCode;
import team.projectpulse.user.UserRepository;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-STU-deactivate-student: the main success scenario and extensions 3a, 3b and 6a (5a is client-only: a cancelled
 * confirm sends no request). Fixtures per design/stu.md: Bill Gates is an instructor of John Smith's course section
 * and not a course admin; Tim Cook teaches another course section; Bingyang Wei is the course admin of John's course.
 * The dev clock is Sunday 2023-08-20.
 */
@DisplayName("Integration tests for UC-STU-deactivate-student")
class StudentDeactivationIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate DEV_TODAY = LocalDate.of(2023, 8, 20);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EntityManager entityManager;

    @Value("${api.endpoint.base-url}")
    String baseUrl;

    Integer johnId;

    Integer johnTeamId;

    String instructorBillToken;

    String adminTimToken;

    String studentJohnToken;


    @BeforeEach
    void setUp() throws Exception {
        Student john = (Student) this.userRepository.findByEmail("j.smith@abc.edu").orElseThrow();
        this.johnId = john.getId();
        this.johnTeamId = john.getTeam().getTeamId();
        this.instructorBillToken = login("b.gates@abc.edu");
        this.adminTimToken = login("t.cook@abc.edu");
        this.studentJohnToken = login("j.smith@abc.edu");
    }

    private String login(String email) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456"))).andReturn();
        JSONObject json = new JSONObject(mvcResult.getResponse().getContentAsString());
        return "Bearer " + json.getJSONObject("data").getString("token");
    }

    private ResultActions deactivate(Integer studentId, String token) throws Exception {
        return this.mockMvc.perform(post(this.baseUrl + "/students/" + studentId + "/deactivate").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions searchSmith(String token) throws Exception {
        return this.mockMvc.perform(post(this.baseUrl + "/students/search").contentType(MediaType.APPLICATION_JSON).content("{\"lastName\":\"Smith\"}").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, token));
    }

    private Student reloadJohn() {
        this.entityManager.flush();
        this.entityManager.clear();
        return this.studentRepository.findById(this.johnId).orElseThrow();
    }

    @Test
    @DisplayName("Main: an instructor of her course section deactivates her; her team is kept; she can no longer log in")
    void instructorOfSectionDeactivatesStudent() throws Exception {
        searchSmith(this.instructorBillToken)
                .andExpect(jsonPath("$.data.content[?(@.id == " + this.johnId + ")].canDeactivate", contains(true)));

        deactivate(this.johnId, this.instructorBillToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.message").value("Deactivate student successfully"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.deactivatedOn").value("2023-08-20"))
                .andExpect(jsonPath("$.data.teamId").value(this.johnTeamId));

        Student john = reloadJohn();
        assertThat(john.isEnabled()).isFalse();
        assertThat(john.getDeactivatedOn()).isEqualTo(DEV_TODAY);
        assertThat(john.getTeam().getTeamId()).isEqualTo(this.johnTeamId);

        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("j.smith@abc.edu", "123456")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Main, FR-SEC-active-account: her token issued before deactivation is refused on her next request")
    void loggedInStudentIsRefusedAfterDeactivation() throws Exception {
        deactivate(this.johnId, this.instructorBillToken).andExpect(status().isOk());

        this.mockMvc.perform(post(this.baseUrl + "/activities/search").contentType(MediaType.APPLICATION_JSON).content("{}").header(HttpHeaders.AUTHORIZATION, this.studentJohnToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Main: the course admin deactivates her without being an instructor of her course section")
    void courseAdminNotInSectionDeactivatesStudent() throws Exception {
        Student john = (Student) this.userRepository.findByEmail("j.smith@abc.edu").orElseThrow();
        Instructor bingyang = (Instructor) this.userRepository.findByEmail("b.wei@abc.edu").orElseThrow();
        assertThat(john.getSection().getCourse().getCourseAdmin().getId()).isEqualTo(bingyang.getId());
        john.getSection().removeInstructor(bingyang);
        this.entityManager.flush();
        String adminBingyangToken = login("b.wei@abc.edu");

        deactivate(this.johnId, adminBingyangToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    @DisplayName("3a: a second deactivation is refused with 409 and keeps her original deactivation date")
    void alreadyDeactivatedIsRefused() throws Exception {
        deactivate(this.johnId, this.instructorBillToken).andExpect(status().isOk());
        Student john = reloadJohn();
        john.setDeactivatedOn(LocalDate.of(2023, 8, 1)); // an earlier date, so "kept" is distinguishable from "rewritten"
        this.studentRepository.saveAndFlush(john);

        deactivate(this.johnId, this.instructorBillToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT))
                .andExpect(jsonPath("$.message").value("Student is already deactivated."));

        assertThat(reloadJohn().getDeactivatedOn()).isEqualTo(LocalDate.of(2023, 8, 1));
        // 3a1: no longer offered
        searchSmith(this.instructorBillToken)
                .andExpect(jsonPath("$.data.content[?(@.id == " + this.johnId + ")].canDeactivate", contains(false)))
                .andExpect(jsonPath("$.data.content[?(@.id == " + this.johnId + ")].deactivatedOn", contains("2023-08-01")));
    }

    @Test
    @DisplayName("3b: an instructor of another course section is refused with a permission error")
    void instructorOfOtherSectionIsForbidden() throws Exception {
        deactivate(this.johnId, this.adminTimToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));

        assertThat(reloadJohn().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("3b: the student herself is refused with a permission error")
    void studentHerselfIsForbidden() throws Exception {
        deactivate(this.johnId, this.studentJohnToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));

        assertThat(reloadJohn().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("6a: a student id that names no row is a permission error, not a 404")
    void deletedStudentIsForbidden() throws Exception {
        deactivate(999999, this.instructorBillToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
    }

    @Test
    @DisplayName("The single-student GET carries her deactivation date")
    void singleStudentGetCarriesDeactivationDate() throws Exception {
        deactivate(this.johnId, this.instructorBillToken).andExpect(status().isOk());

        this.mockMvc.perform(get(this.baseUrl + "/students/" + this.johnId).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.instructorBillToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deactivatedOn").value("2023-08-20"))
                .andExpect(jsonPath("$.data.canDeactivate").doesNotExist());
    }

    private ResultActions putJohn(boolean enabled, String token) throws Exception {
        String body = "{\"username\":\"j.smith@abc.edu\",\"firstName\":\"John\",\"lastName\":\"Smith\",\"email\":\"j.smith@abc.edu\",\"enabled\":" + enabled + "}";
        return this.mockMvc.perform(put(this.baseUrl + "/students/" + this.johnId).contentType(MediaType.APPLICATION_JSON).content(body).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, token));
    }

    @Test
    @DisplayName("Interim PUT: enabled false no longer deactivates her")
    void putCannotDeactivate() throws Exception {
        putJohn(false, this.instructorBillToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        Student john = reloadJohn();
        assertThat(john.isEnabled()).isTrue();
        assertThat(john.getDeactivatedOn()).isNull();
    }

    @Test
    @DisplayName("Interim PUT: enabled true from a permitted caller re-enables her and clears her deactivation date")
    void putReEnablesAndClearsDate() throws Exception {
        deactivate(this.johnId, this.instructorBillToken).andExpect(status().isOk());

        putJohn(true, this.instructorBillToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        Student john = reloadJohn();
        assertThat(john.isEnabled()).isTrue();
        assertThat(john.getDeactivatedOn()).isNull();
    }

}
