package team.projectpulse.security;

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
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.system.StatusCode;
import team.projectpulse.user.PeerEvaluationUser;
import team.projectpulse.user.UserRepository;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-SEC-active-account: a token issued while the account was active is refused on the very next request once
 * the account is deactivated, for students and instructors alike. The account is disabled directly through the
 * repository here, so this covers the shared check independently of the deactivation use case that sets it.
 */
@DisplayName("Integration tests for the per-request active-account check")
class ActiveAccountIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Value("${api.endpoint.base-url}")
    String baseUrl;

    String studentJohnToken;

    String instructorBillToken;


    @BeforeEach
    void setUp() throws Exception {
        this.studentJohnToken = login("j.smith@abc.edu");
        this.instructorBillToken = login("b.gates@abc.edu");
    }

    private String login(String email) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456"))).andReturn();
        JSONObject json = new JSONObject(mvcResult.getResponse().getContentAsString());
        return "Bearer " + json.getJSONObject("data").getString("token");
    }

    private void disable(String email) {
        PeerEvaluationUser user = this.userRepository.findByEmail(email).orElseThrow();
        user.setEnabled(false);
        this.userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("A student's unexpired token is refused once her account is deactivated")
    void studentTokenRefusedAfterDeactivation() throws Exception {
        this.mockMvc.perform(post(this.baseUrl + "/activities/search").contentType(MediaType.APPLICATION_JSON).content("{}").header(HttpHeaders.AUTHORIZATION, this.studentJohnToken))
                .andExpect(status().isOk());

        disable("j.smith@abc.edu");

        this.mockMvc.perform(post(this.baseUrl + "/activities/search").contentType(MediaType.APPLICATION_JSON).content("{}").header(HttpHeaders.AUTHORIZATION, this.studentJohnToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED))
                .andExpect(jsonPath("$.data", containsString("This account is deactivated.")));
    }

    @Test
    @DisplayName("An instructor's unexpired token is refused once his account is deactivated")
    void instructorTokenRefusedAfterDeactivation() throws Exception {
        this.mockMvc.perform(post(this.baseUrl + "/students/search").contentType(MediaType.APPLICATION_JSON).content("{}").header(HttpHeaders.AUTHORIZATION, this.instructorBillToken))
                .andExpect(status().isOk());

        disable("b.gates@abc.edu");

        this.mockMvc.perform(post(this.baseUrl + "/students/search").contentType(MediaType.APPLICATION_JSON).content("{}").header(HttpHeaders.AUTHORIZATION, this.instructorBillToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("A deactivated account cannot log in again to get a fresh token")
    void deactivatedAccountCannotLogIn() throws Exception {
        disable("j.smith@abc.edu");

        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("j.smith@abc.edu", "123456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

}
