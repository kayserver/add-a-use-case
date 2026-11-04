package team.projectpulse.evaluation;

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
import tools.jackson.databind.json.JsonMapper;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.evaluation.dto.PeerEvaluationDto;
import team.projectpulse.evaluation.dto.RatingDto;
import team.projectpulse.student.Student;
import team.projectpulse.student.StudentRepository;
import team.projectpulse.system.StatusCode;
import team.projectpulse.user.UserRepository;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-deactivated-student-weeks, as UC-STU-deactivate-student leaves it: John Smith, deactivated on 2023-08-01
 * (week 2023-W31), stays on his team, and his teammate Eric Hudson may no longer evaluate him for a later week.
 * The dev clock is Sunday 2023-08-20 (week 2023-W33), so the week open for peer evaluations is 2023-W32.
 */
@DisplayName("Integration tests for BR-deactivated-student-weeks")
class DeactivatedStudentWeeksIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    UserRepository userRepository;

    @Value("${api.endpoint.base-url}")
    String baseUrl;

    Student john;

    Student eric;

    String studentEricToken;


    @BeforeEach
    void setUp() throws Exception {
        this.john = (Student) this.userRepository.findByEmail("j.smith@abc.edu").orElseThrow();
        this.eric = (Student) this.userRepository.findByEmail("e.hudson@abc.edu").orElseThrow();
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("e.hudson@abc.edu", "123456"))).andReturn();
        this.studentEricToken = "Bearer " + new JSONObject(mvcResult.getResponse().getContentAsString()).getJSONObject("data").getString("token");

        this.john.setEnabled(false);
        this.john.setDeactivatedOn(LocalDate.of(2023, 8, 1)); // Tuesday of 2023-W31
        this.studentRepository.saveAndFlush(this.john);
    }

    private ResultActions teammates(String week) throws Exception {
        return this.mockMvc.perform(get(this.baseUrl + "/students/teams/" + this.john.getTeam().getTeamId()).param("week", week).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.studentEricToken));
    }

    @Test
    @DisplayName("A teammate may not evaluate him for a week after his deactivation week")
    void evaluationAfterDeactivationWeekIsRefused() throws Exception {
        List<RatingDto> ratings = List.of(new RatingDto(null, 1, 4.0), new RatingDto(null, 2, 9.0), new RatingDto(null, 3, 7.0),
                new RatingDto(null, 4, 10.0), new RatingDto(null, 5, 5.0), new RatingDto(null, 6, 6.0));
        PeerEvaluationDto dto = new PeerEvaluationDto(null, "2023-W32", this.eric.getId(), "Eric Hudson", this.john.getId(), "John Smith", ratings, 41.0, "Good job", "Keep it up", null, null);

        this.mockMvc.perform(post(this.baseUrl + "/evaluations").contentType(MediaType.APPLICATION_JSON).content(this.jsonMapper.writeValueAsString(dto)).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.studentEricToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.INVALID_ARGUMENT))
                .andExpect(jsonPath("$.message").value("John was deactivated before that week and cannot be evaluated for it."));
    }

    @Test
    @DisplayName("He stays on his team: the teammates for a later week leave him out, for his deactivation week include him")
    void teammatesForAWeek() throws Exception {
        teammates("2023-W32")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", not(hasItem(this.john.getId()))))
                .andExpect(jsonPath("$.data[*].id", hasItem(this.eric.getId())));

        teammates("2023-W31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(this.john.getId())))
                .andExpect(jsonPath("$.data[*].deactivatedOn", everyItem(nullValue())));

        // Without a week the list is unchanged: every member, him included
        this.mockMvc.perform(get(this.baseUrl + "/students/teams/" + this.john.getTeam().getTeamId()).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.studentEricToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(this.john.getId())));
    }

}
