package team.projectpulse.student;

import jakarta.persistence.EntityManager;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.system.StatusCode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("UC-STU-deactivate-student")
class DeactivateStudentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager entityManager;

    @Value("${api.endpoint.base-url}")
    String baseUrl;

    String courseAdminToken;
    String instructorBillToken;
    String studentJohnToken;
    String otherSectionInstructorToken;


    @BeforeEach
    void setUp() throws Exception {
        this.courseAdminToken = login("b.wei@abc.edu", "123456");
        this.instructorBillToken = login("b.gates@abc.edu", "123456");
        this.studentJohnToken = login("j.smith@abc.edu", "123456");
        this.otherSectionInstructorToken = login("t.cook@abc.edu", "123456");
    }

    @Test
    void testHerInstructorDeactivatesHerAndKeepsHerWork() throws Exception {
        Student john = findJohn();
        Integer teamId = john.getTeam().getTeamId();
        long activities = count("select count(a) from Activity a where a.student.email = 'j.smith@abc.edu'");
        long evaluations = count("select count(e) from PeerEvaluation e where e.evaluatee.email = 'j.smith@abc.edu'");
        assertThat(activities).isPositive();
        assertThat(evaluations).isPositive();

        this.mockMvc.perform(post(this.baseUrl + "/students/" + john.getId() + "/deactivation")
                        .header(HttpHeaders.AUTHORIZATION, this.instructorBillToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.message").value("This student has been deactivated."))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.teamId").value(teamId));

        this.entityManager.clear();
        assertThat(findJohn().isEnabled()).isFalse();
        assertThat(findJohn().getTeam().getTeamId()).isEqualTo(teamId);
        assertThat(count("select count(a) from Activity a where a.student.email = 'j.smith@abc.edu'")).isEqualTo(activities);
        assertThat(count("select count(e) from PeerEvaluation e where e.evaluatee.email = 'j.smith@abc.edu'")).isEqualTo(evaluations);
    }

    @Test
    void testHerCourseAdminDeactivatesHerWhenSheIsNotOnTheCourseSection() throws Exception {
        Student john = findJohn();
        Instructor courseAdmin = john.getSection().getCourse().getCourseAdmin();
        john.getSection().removeInstructor(courseAdmin);
        this.entityManager.flush();
        assertThat(john.getSection().getInstructors()).doesNotContain(courseAdmin);
        Integer teamId = john.getTeam().getTeamId();

        this.mockMvc.perform(post(this.baseUrl + "/students/" + john.getId() + "/deactivation")
                        .header(HttpHeaders.AUTHORIZATION, this.courseAdminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("This student has been deactivated."))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.teamId").value(teamId));
    }

    @Test
    void testASecondDeactivationIsRefused() throws Exception {
        Student john = findJohn();
        deactivate(john.getId(), this.instructorBillToken);

        this.mockMvc.perform(post(this.baseUrl + "/students/" + john.getId() + "/deactivation")
                        .header(HttpHeaders.AUTHORIZATION, this.instructorBillToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.INVALID_ARGUMENT))
                .andExpect(jsonPath("$.message").value("This student's account is already deactivated."));

        this.entityManager.clear();
        assertThat(findJohn().isEnabled()).isFalse();
    }

    @Test
    void testAnInstructorOfAnotherCourseSectionIsRefused() throws Exception {
        Student john = findJohn();

        this.mockMvc.perform(post(this.baseUrl + "/students/" + john.getId() + "/deactivation")
                        .header(HttpHeaders.AUTHORIZATION, this.otherSectionInstructorToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN))
                .andExpect(jsonPath("$.message").value("No permission."));

        this.entityManager.clear();
        assertThat(findJohn().isEnabled()).isTrue();
    }

    @Test
    void testTheStudentHerselfIsRefused() throws Exception {
        Student john = findJohn();

        this.mockMvc.perform(post(this.baseUrl + "/students/" + john.getId() + "/deactivation")
                        .header(HttpHeaders.AUTHORIZATION, this.studentJohnToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));

        this.entityManager.clear();
        assertThat(findJohn().isEnabled()).isTrue();
    }

    @Test
    void testAnUnknownStudentIsRefused() throws Exception {
        this.mockMvc.perform(post(this.baseUrl + "/students/999999/deactivation")
                        .header(HttpHeaders.AUTHORIZATION, this.instructorBillToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
    }

    @Test
    void testAnInstructorCannotDeactivateHerByEditingEnabled() throws Exception {
        Student john = findJohn();

        String json = """
                {"username":"j.smith@abc.edu","firstName":"John (updated)","lastName":"Smith","email":"j.smith@abc.edu","enabled":false}
                """;

        this.mockMvc.perform(put(this.baseUrl + "/students/" + john.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .header(HttpHeaders.AUTHORIZATION, this.instructorBillToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("John (updated)"))
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    void testLoginWithADeactivatedAccountIsRefused() throws Exception {
        findJohn().setEnabled(false);
        this.entityManager.flush();

        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("j.smith@abc.edu", "123456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED))
                .andExpect(jsonPath("$.message").value("User account is abnormal."));
    }

    @Test
    void testATokenIssuedBeforeDeactivationIsRefused() throws Exception {
        findJohn().setEnabled(false);
        this.entityManager.flush();

        this.mockMvc.perform(get(this.baseUrl + "/students/" + findJohn().getId())
                        .header(HttpHeaders.AUTHORIZATION, this.studentJohnToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED))
                .andExpect(jsonPath("$.message").value("The access token provided is expired, revoked, malformed, or invalid for other reasons."));
    }

    @Test
    void testADeactivatedInstructorsTokenIsRefused() throws Exception {
        this.entityManager.createQuery("select i from Instructor i where i.email = 'b.gates@abc.edu'", Instructor.class)
                .getSingleResult()
                .setEnabled(false);
        this.entityManager.flush();

        this.mockMvc.perform(get(this.baseUrl + "/students/" + findJohn().getId())
                        .header(HttpHeaders.AUTHORIZATION, this.instructorBillToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("The access token provided is expired, revoked, malformed, or invalid for other reasons."));
    }

    private void deactivate(Integer studentId, String token) throws Exception {
        this.mockMvc.perform(post(this.baseUrl + "/students/" + studentId + "/deactivation")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private Student findJohn() {
        return this.entityManager.createQuery("select s from Student s where s.email = 'j.smith@abc.edu'", Student.class)
                .getSingleResult();
    }

    private long count(String jpql) {
        return this.entityManager.createQuery(jpql, Long.class).getSingleResult();
    }

    private String login(String username, String password) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(username, password)))
                .andReturn();
        JSONObject json = new JSONObject(mvcResult.getResponse().getContentAsString());
        return "Bearer " + json.getJSONObject("data").getString("token");
    }

}
