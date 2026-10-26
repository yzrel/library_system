package ph.edu.vfgnhs.library;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LibraryApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void publicCannotRegisterAdministrator() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"intruder@test.local","password":"Password1","firstName":"Bad","lastName":"Actor",
                 "role":"ADMIN","schoolId":"X-1"}
                """)).andExpect(status().isBadRequest());
    }

    @Test
    void studentRegistrationGetsPortalButNotAdminAccess() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"student1@test.local","password":"Password1","firstName":"Ana","lastName":"Cruz",
                 "role":"STUDENT","schoolId":"LRN-1001","gradeSection":"Grade 10 - Rizal"}
                """)).andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(body).get("token").asText();
        mvc.perform(get("/api/portal/loans").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateCatalogAndDuplicateAccessionIsRejected() throws Exception {
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.local\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(login).get("token").asText();
        String bookResponse = mvc.perform(post("/api/admin/catalog/books")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Test Library Book\",\"author\":\"VFGNHS\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode book = json.readTree(bookResponse);
        long id = book.get("id").asLong();
        String copy = "{\"accessionNumber\":\"TEST-ACC-001\",\"callNumber\":\"001\",\"status\":\"AVAILABLE\"}";
        mvc.perform(post("/api/admin/catalog/books/" + id + "/copies")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(copy))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/catalog/books/" + id + "/copies")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(copy))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/opac/books?q=Test")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title=='Test Library Book')].availableCopies")
                        .value(org.hamcrest.Matchers.hasItem(1)));
    }

    @Test
    void adminCanEditBookAndCopyAndLoginRecordsLocation() throws Exception {
        String login = mvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "192.168.30.15")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@test.local\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(login).get("token").asText();

        String bookResponse = mvc.perform(post("/api/admin/catalog/books")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Editable Title\",\"author\":\"First Author\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bookId = json.readTree(bookResponse).get("id").asLong();
        String copyResponse = mvc.perform(post("/api/admin/catalog/books/" + bookId + "/copies")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessionNumber\":\"EDIT-001\",\"callNumber\":\"100\",\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long copyId = json.readTree(copyResponse).get("id").asLong();

        mvc.perform(put("/api/admin/catalog/books/" + bookId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Corrected Title\",\"author\":\"Revised Author\",\"subject\":\"Fiction\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Corrected Title"))
                .andExpect(jsonPath("$.author").value("Revised Author"));
        mvc.perform(put("/api/admin/catalog/copies/" + copyId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessionNumber\":\"EDIT-001\",\"callNumber\":\"200\",\"status\":\"DAMAGED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callNumber").value("200"))
                .andExpect(jsonPath("$.status").value("DAMAGED"));

        mvc.perform(get("/api/admin/audit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action=='LOGIN')].actorName").value(org.hamcrest.Matchers.hasItem("System Administrator")))
                .andExpect(jsonPath("$[?(@.action=='LOGIN')].location").value(org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("School network (192.168.30.15)"))))
                .andExpect(jsonPath("$[?(@.action=='LOGIN')].ipAddress").value(org.hamcrest.Matchers.hasItem("192.168.30.15")));
    }

    @Test
    void adminCanDeleteCopyWithoutLoanHistory() throws Exception {
        String token = adminToken();
        String bookResponse = mvc.perform(post("/api/admin/catalog/books")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Disposable Copy Title\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bookId = json.readTree(bookResponse).get("id").asLong();
        String copyResponse = mvc.perform(post("/api/admin/catalog/books/" + bookId + "/copies")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessionNumber\":\"DEL-001\",\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long copyId = json.readTree(copyResponse).get("id").asLong();

        mvc.perform(delete("/api/admin/catalog/copies/" + copyId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/catalog/books?q=Disposable Copy Title").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalCopies").value(0));
        mvc.perform(get("/api/admin/audit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action=='DELETE_COPY')].details")
                        .value(org.hamcrest.Matchers.hasItem("DEL-001")));

        String loaned = mvc.perform(post("/api/admin/catalog/books/" + bookId + "/copies")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessionNumber\":\"DEL-LOAN\",\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long loanedId = json.readTree(loaned).get("id").asLong();
        String registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"delete-copy@test.local","password":"Password1","firstName":"Del","lastName":"Copy",
                 "role":"STUDENT","schoolId":"LRN-DEL-1","gradeSection":"Grade 10 - Rizal"}
                """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long borrowerId = json.readTree(registration).get("user").get("borrowerId").asLong();
        String checkout = mvc.perform(post("/api/admin/circulation/checkout")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"copyId\":" + loanedId + ",\"borrowerId\":" + borrowerId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(delete("/api/admin/catalog/copies/" + loanedId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/circulation/" + json.readTree(checkout).get("id").asLong() + "/return")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/admin/catalog/copies/" + loanedId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanAutoAssignAndBulkCreateCopies() throws Exception {
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@test.local\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(login).get("token").asText();

        String singleBookResponse = mvc.perform(post("/api/admin/catalog/books")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Automatic Accession Test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long singleBookId = json.readTree(singleBookResponse).get("id").asLong();
        mvc.perform(post("/api/admin/catalog/books/" + singleBookId + "/copies")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"callNumber\":\"AUTO\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessionNumber").value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{4}")));

        String bulkBookResponse = mvc.perform(post("/api/admin/catalog/books")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Bulk Copy Test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bulkBookId = json.readTree(bulkBookResponse).get("id").asLong();
        mvc.perform(post("/api/admin/catalog/books/" + bulkBookId + "/copies/batch")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":5,"startingAccessionNumber":"BULK-0100",
                                 "callNumber":"500","dateReceived":"2026-10-26","sourceOfFund":"MOOE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.copies.length()").value(5))
                .andExpect(jsonPath("$.firstAccession").value("BULK-0100"))
                .andExpect(jsonPath("$.lastAccession").value("BULK-0104"));

        mvc.perform(get("/api/opac/books").param("q", "Bulk Copy Test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availableCopies").value(5));

        mvc.perform(post("/api/admin/catalog/books/" + bulkBookId + "/copies/batch")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2,\"startingAccessionNumber\":\"BULK-0104\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/catalog/books/" + bulkBookId + "/copies/batch")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/catalog/books/" + bulkBookId + "/copies/batch")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":201}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/admin/catalog/intake")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"existingBookId":%d,"title":"Bulk Copy Test","quantity":3,
                                 "callNumber":"500","status":"AVAILABLE"}
                                """.formatted(bulkBookId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBook").value(false))
                .andExpect(jsonPath("$.createdCopies.length()").value(3))
                .andExpect(jsonPath("$.copies.length()").value(8));
    }

    @Test
    void adminIsbnIntakeCreatesBookAndCopyThenReusesTitle() throws Exception {
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.local\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(login).get("token").asText();

        mvc.perform(get("/api/admin/catalog/isbn-lookup").param("isbn", "123")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        String first = mvc.perform(post("/api/admin/catalog/intake")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Scanned Title","author":"Scan Author","isbn":"9780306406157",
                                 "publisher":"Test Press","publicationYear":1988,
                                 "accessionNumber":"INTAKE-001","callNumber":"500","status":"AVAILABLE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBook").value(true))
                .andExpect(jsonPath("$.isbn").value("9780306406157"))
                .andExpect(jsonPath("$.totalCopies").value(1))
                .andExpect(jsonPath("$.copy.accessionNumber").value("INTAKE-001"))
                .andReturn().getResponse().getContentAsString();
        long bookId = json.readTree(first).get("id").asLong();

        mvc.perform(get("/api/admin/catalog/isbn-lookup").param("isbn", "0-306-40615-2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.source").value("LOCAL"))
                .andExpect(jsonPath("$.existingBookId").value(bookId))
                .andExpect(jsonPath("$.title").value("Scanned Title"))
                .andExpect(jsonPath("$.suggestedAccessionNumber").value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{4}")));

        String auto = mvc.perform(post("/api/admin/catalog/intake")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Auto Accession Book","isbn":"9780140328721","author":"Dahl"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.copy.accessionNumber").value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{4}")))
                .andReturn().getResponse().getContentAsString();
        String autoAcc = json.readTree(auto).path("copy").path("accessionNumber").asText();

        mvc.perform(get("/api/admin/catalog/next-accession").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessionNumber").value(org.hamcrest.Matchers.not(autoAcc)));

        mvc.perform(post("/api/admin/catalog/intake")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"existingBookId":%d,"title":"Scanned Title","isbn":"9780306406157",
                                 "accessionNumber":"INTAKE-002","status":"AVAILABLE"}
                                """.formatted(bookId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBook").value(false))
                .andExpect(jsonPath("$.copy.accessionNumber").value("INTAKE-002"));

        mvc.perform(post("/api/admin/catalog/intake")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Duplicate Acc","isbn":"9780140328721","accessionNumber":"INTAKE-001"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanChangeAccountLoginAndResetPassword() throws Exception {
        String student = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"credentials@test.local","password":"Password1","firstName":"Lina","lastName":"Santos",
                 "role":"STUDENT","schoolId":"LRN-2002","gradeSection":"Grade 9 - Mabini"}
                """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long studentId = json.readTree(student).get("user").get("id").asLong();
        String studentToken = json.readTree(student).get("token").asText();

        String admin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.local\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(admin).get("token").asText();

        mvc.perform(patch("/api/admin/accounts/" + studentId)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"lina.reader\",\"password\":\"Reader123\",\"role\":\"TEACHER\",\"status\":\"ACTIVE\"," +
                        "\"gradeSection\":\"Grade 9 - Advisory\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("lina.reader"))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.gradeSection").value("Grade 9 - Advisory"));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("lina.reader"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"lina.reader\",\"password\":\"Reader123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void todayAttendanceListIsPublicAndShowsCurrentDayVisits() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"todaylist@test.local","password":"Password1","firstName":"Nora","lastName":"Cruz",
                 "role":"STUDENT","schoolId":"LRN-TODAY-1","gradeSection":"Grade 9 - Mabini"}
                """)).andExpect(status().isCreated());
        mvc.perform(post("/api/attendance/check-in").contentType(MediaType.APPLICATION_JSON).content("""
                {"identifier":"LRN-TODAY-1","purpose":"READING","entryMethod":"MANUAL"}
                """)).andExpect(status().isCreated());
        mvc.perform(get("/api/attendance/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.visits[0].name").value("Nora Cruz"))
                .andExpect(jsonPath("$.visits[0].email").doesNotExist());
    }

    @Test
    void attendanceSuggestReturnsActiveBorrowerMatchesByNameOrId() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"suggest@test.local","password":"Password1","firstName":"Kara","lastName":"Lopez",
                 "role":"STUDENT","schoolId":"LRN-SUG-1","gradeSection":"Grade 11 - Bonifacio"}
                """)).andExpect(status().isCreated());
        mvc.perform(get("/api/attendance/suggest").param("q", "Kar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Kara Lopez"))
                .andExpect(jsonPath("$[0].identifier").value("S-LRN-SUG-1"))
                .andExpect(jsonPath("$[0].email").doesNotExist());
        mvc.perform(get("/api/attendance/suggest").param("q", "LRN-SUG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].schoolId").value("LRN-SUG-1"));
    }

    @Test
    void publicAttendanceSupportsFeedbackAndProtectsPrivateAccountData() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"attendance@test.local","password":"Password1","firstName":"Mia","lastName":"Reyes",
                 "role":"STUDENT","schoolId":"LRN-ATT-1","gradeSection":"Grade 8 - Luna"}
                """)).andExpect(status().isCreated());

        String checkIn = mvc.perform(post("/api/attendance/check-in").contentType(MediaType.APPLICATION_JSON).content("""
                {"identifier":"LRN-ATT-1","purpose":"READING","entryMethod":"MANUAL"}
                """)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Mia Reyes"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.schoolId").doesNotExist())
                .andExpect(jsonPath("$.cardNumber").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long visitId = json.readTree(checkIn).get("visitId").asLong();

        mvc.perform(post("/api/attendance/check-in").contentType(MediaType.APPLICATION_JSON).content("""
                {"identifier":"S-LRN-ATT-1","purpose":"STUDY","entryMethod":"QR"}
                """)).andExpect(status().isConflict());
        mvc.perform(post("/api/attendance/feedback").contentType(MediaType.APPLICATION_JSON)
                .content("{\"visitId\":" + visitId + ",\"rating\":\"SATISFIED\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rating").value("SATISFIED"));
        mvc.perform(post("/api/attendance/feedback").contentType(MediaType.APPLICATION_JSON)
                .content("{\"visitId\":" + visitId + ",\"rating\":\"NEUTRAL\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void attendanceLogbookIsAdminOnlyAndExportsCsv() throws Exception {
        mvc.perform(get("/api/admin/attendance")).andExpect(status().isUnauthorized());
        String token = adminToken();
        mvc.perform(get("/api/admin/attendance").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visits").isArray())
                .andExpect(jsonPath("$.feedback.SATISFIED").isNumber());
        mvc.perform(get("/api/admin/attendance/export.csv").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=attendance-logbook.csv"))
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    void inactiveBorrowerCannotCheckInAndPurposeIsValidated() throws Exception {
        String registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"blocked@test.local","password":"Password1","firstName":"Bo","lastName":"Locked",
                 "role":"STUDENT","schoolId":"LRN-BLOCKED","gradeSection":"Grade 7"}
                """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long userId = json.readTree(registration).get("user").get("id").asLong();
        mvc.perform(post("/api/attendance/check-in").contentType(MediaType.APPLICATION_JSON).content("""
                {"identifier":"LRN-BLOCKED","purpose":"INVALID","entryMethod":"MANUAL"}
                """)).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/accounts/" + userId).header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/attendance/check-in").contentType(MediaType.APPLICATION_JSON).content("""
                {"identifier":"LRN-BLOCKED","purpose":"READING","entryMethod":"MANUAL"}
                """)).andExpect(status().isForbidden());
    }

    @Test
    void circulationRecordsIncludeBorrowerGradeSection() throws Exception {
        String registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"circ-student@test.local","password":"Password1","firstName":"Kara","lastName":"Lopez",
                 "role":"STUDENT","schoolId":"LRN-CIRC-1","gradeSection":"Grade 11 - Bonifacio"}
                """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long borrowerId = json.readTree(registration).get("user").get("borrowerId").asLong();
        String token = adminToken();

        String bookResponse = mvc.perform(post("/api/admin/catalog/books")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Circulation Grade Book\",\"author\":\"VFGNHS\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bookId = json.readTree(bookResponse).get("id").asLong();
        String copyResponse = mvc.perform(post("/api/admin/catalog/books/" + bookId + "/copies")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accessionNumber\":\"CIRC-ACC-001\",\"callNumber\":\"100\",\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long copyId = json.readTree(copyResponse).get("id").asLong();

        mvc.perform(post("/api/admin/circulation/checkout")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"copyId\":" + copyId + ",\"borrowerId\":" + borrowerId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gradeSection").value("Grade 11 - Bonifacio"));

        mvc.perform(get("/api/admin/circulation").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.accessionNumber=='CIRC-ACC-001')].gradeSection").value("Grade 11 - Bonifacio"));
    }

    @Test
    void classDistributionSupportsPreviewReviewReturnsFiltersAndCards() throws Exception {
        String teacherRegistration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"distribution-teacher@test.local","password":"Password1","firstName":"Tess","lastName":"Adviser",
                 "role":"TEACHER","schoolId":"T-DIST-1","department":"Science"}
                """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long teacherId = json.readTree(teacherRegistration).get("user").get("borrowerId").asLong();
        String token = adminToken();
        createClassAndAssign(token, teacherId, "Grade 10", "Rizal", "2026-2027");
        String bookResponse = mvc.perform(post("/api/admin/catalog/books").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Distribution Science Set\",\"author\":\"VFGNHS\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bookId = json.readTree(bookResponse).get("id").asLong();
        long[] copyIds = new long[2];
        for (int index = 0; index < 2; index++) {
            String copy = mvc.perform(post("/api/admin/catalog/books/" + bookId + "/copies")
                    .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"accessionNumber\":\"DIST-ACC-00" + (index + 1) + "\",\"status\":\"AVAILABLE\"}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            copyIds[index] = json.readTree(copy).get("id").asLong();
        }
        String due = LocalDate.now().plusMonths(6).toString();
        String header = "\"teacherBorrowerId\":" + teacherId +
                ",\"gradeSection\":\"Grade 10 - Rizal\",\"schoolYear\":\"2026-2027\",\"expectedReturnDate\":\"" + due + "\"";
        mvc.perform(post("/api/admin/distributions/preview").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + header + ",\"lines\":[{\"bookId\":" + bookId + ",\"quantity\":3}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].proposedCopies.length()").value(2))
                .andExpect(jsonPath("$.lines[0].shortfall").value(1));

        String created = mvc.perform(post("/api/admin/distributions").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + header + ",\"notes\":\"Advisory class set\",\"lines\":[{\"bookId\":" + bookId +
                        ",\"copyIds\":[" + copyIds[0] + "," + copyIds[1] + "]}]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.activeCopies").value(2))
                .andExpect(jsonPath("$.loans[0].distributionSchoolYear").value("2026-2027"))
                .andReturn().getResponse().getContentAsString();
        JsonNode distribution = json.readTree(created);
        long distributionId = distribution.get("id").asLong();
        long firstLoanId = distribution.get("loans").get(0).get("id").asLong();
        long secondLoanId = distribution.get("loans").get(1).get("id").asLong();

        mvc.perform(post("/api/admin/distributions/" + distributionId + "/returns")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loanIds\":[" + firstLoanId + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PARTIALLY_RETURNED"))
                .andExpect(jsonPath("$.activeCopies").value(1));
        mvc.perform(post("/api/admin/circulation/" + secondLoanId + "/return").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/distributions").header("Authorization", "Bearer " + token)
                .param("q", "Rizal").param("schoolYear", "2026-2027").param("status", "RETURNED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(distributionId))
                .andExpect(jsonPath("$[0].returnedCopies").value(2));
        mvc.perform(get("/api/admin/print/book-card/" + copyIds[0]).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions[0].borrowerName").value("Tess Adviser"))
                .andExpect(jsonPath("$.transactions[0].distributionId").value(distributionId));
    }

    @Test
    void classDistributionRejectsDuplicateTitlesAndAlreadyAssignedCopies() throws Exception {
        String teacherRegistration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"distribution-lock@test.local","password":"Password1","firstName":"Luz","lastName":"Teacher",
                 "role":"TEACHER","schoolId":"T-DIST-2","department":"English"}
                """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long teacherId = json.readTree(teacherRegistration).get("user").get("borrowerId").asLong();
        String token = adminToken();
        createClassAndAssign(token, teacherId, "Grade 9", "Luna", "2026-2027");
        String book = mvc.perform(post("/api/admin/catalog/books").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Distribution Locking Set\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bookId = json.readTree(book).get("id").asLong();
        String copy = mvc.perform(post("/api/admin/catalog/books/" + bookId + "/copies")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accessionNumber\":\"DIST-LOCK-001\",\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long copyId = json.readTree(copy).get("id").asLong();
        String due = LocalDate.now().plusMonths(5).toString();
        String header = "\"teacherBorrowerId\":" + teacherId +
                ",\"gradeSection\":\"Grade 9 - Luna\",\"schoolYear\":\"2026-2027\",\"expectedReturnDate\":\"" + due + "\"";
        mvc.perform(post("/api/admin/distributions/preview").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + header + ",\"lines\":[{\"bookId\":" + bookId + ",\"quantity\":1},{\"bookId\":" + bookId + ",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());
        String request = "{" + header + ",\"lines\":[{\"bookId\":" + bookId + ",\"copyIds\":[" + copyId + "]}]}";
        mvc.perform(post("/api/admin/distributions").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated());
        mvc.perform(post("/api/admin/distributions").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isConflict());
    }

    @Test
    void adminBorrowerListIncludesAssignedClassSection() throws Exception {
        String token = adminToken();
        String schoolYear = ClassSectionController.currentSchoolYear();
        String created = mvc.perform(post("/api/admin/classes").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gradeLevel\":\"Grade 8\",\"sectionName\":\"Bonifacio\",\"schoolYear\":\"" + schoolYear + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long classId = json.readTree(created).get("id").asLong();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"borrower-list@test.local\",\"password\":\"Password1\",\"firstName\":\"Listed\",\"lastName\":\"Borrower\"," +
                        "\"role\":\"STUDENT\",\"schoolId\":\"LRN-LIST-1\",\"classSectionId\":" + classId + "}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/admin/borrowers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='borrower-list@test.local')].classSectionId")
                        .value(org.hamcrest.Matchers.hasItem((int) classId)));
        mvc.perform(get("/api/admin/borrowers").param("q", "Listed").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].classSectionId").value(classId));
    }

    @Test
    void adminDashboardReturnsCountSummaries() throws Exception {
        String token = adminToken();
        mvc.perform(get("/api/admin/reports/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titles").isNumber())
                .andExpect(jsonPath("$.available").isNumber())
                .andExpect(jsonPath("$.activeLoans").isNumber())
                .andExpect(jsonPath("$.topTitles").isArray())
                .andExpect(jsonPath("$.topBorrowers").isArray());
    }

    @Test
    void studentCanReserveATitleOnceWhilePending() throws Exception {
        String token = adminToken();
        String book = mvc.perform(post("/api/admin/catalog/books")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Reserved Noli\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bookId = json.readTree(book).get("id").asLong();
        String registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"reserve-student@test.local","password":"Password1","firstName":"Res","lastName":"Student",
                 "role":"STUDENT","schoolId":"LRN-RES-1","gradeSection":"Grade 10 - Rizal"}
                """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String studentToken = json.readTree(registration).get("token").asText();
        mvc.perform(post("/api/portal/reservations").header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":" + bookId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Reserved Noli"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post("/api/portal/reservations").header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":" + bookId + "}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/portal/reservations").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Reserved Noli"));
    }

    @Test
    void checkoutRejectsUnavailableCopyAndAutoAccessionUsesManilaYear() throws Exception {
        String token = adminToken();
        String book = mvc.perform(post("/api/admin/catalog/books")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Locked Copy Book\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bookId = json.readTree(book).get("id").asLong();
        String copy = mvc.perform(post("/api/admin/catalog/books/" + bookId + "/copies")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessionNumber\":\"LOCK-001\",\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long copyId = json.readTree(copy).get("id").asLong();
        String registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"lock-student@test.local","password":"Password1","firstName":"Lock","lastName":"Student",
                 "role":"STUDENT","schoolId":"LRN-LOCK-1","gradeSection":"Grade 10 - Rizal"}
                """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long borrowerId = json.readTree(registration).get("user").get("borrowerId").asLong();
        mvc.perform(post("/api/admin/circulation/checkout")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"copyId\":" + copyId + ",\"borrowerId\":" + borrowerId + "}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/circulation/checkout")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"copyId\":" + copyId + ",\"borrowerId\":" + borrowerId + "}"))
                .andExpect(status().isConflict());
        String year = String.valueOf(java.time.Year.now(java.time.ZoneId.of("Asia/Manila")).getValue());
        mvc.perform(get("/api/admin/catalog/next-accession").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessionNumber").value(org.hamcrest.Matchers.startsWith(year + "-")));
    }

    @Test
    void invalidCopyStatusAndNormalizedCardCollisionReturnClientErrors() throws Exception {
        String token = adminToken();
        String book = mvc.perform(post("/api/admin/catalog/books")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Status Guard Book\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bookId = json.readTree(book).get("id").asLong();
        String copy = mvc.perform(post("/api/admin/catalog/books/" + bookId + "/copies")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessionNumber\":\"STAT-001\",\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long copyId = json.readTree(copy).get("id").asLong();
        mvc.perform(put("/api/admin/catalog/copies/" + copyId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessionNumber\":\"STAT-001\",\"status\":\"NOT_A_STATUS\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"card-one@test.local","password":"Password1","firstName":"Card","lastName":"One",
                 "role":"STUDENT","schoolId":"CARD 9","gradeSection":"Grade 10 - Rizal"}
                """)).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"card-two@test.local","password":"Password1","firstName":"Card","lastName":"Two",
                 "role":"STUDENT","schoolId":"CARD9","gradeSection":"Grade 10 - Rizal"}
                """)).andExpect(status().isConflict());
    }

    @Test
    void classDirectoryControlsStudentRegistrationAndTeacherAdvisers() throws Exception {
        String token = adminToken();
        String schoolYear = ClassSectionController.currentSchoolYear();
        String created = mvc.perform(post("/api/admin/classes").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gradeLevel\":\"Grade 7\",\"sectionName\":\"Mabini\",\"schoolYear\":\"" + schoolYear + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.label").value("Grade 7 - Mabini"))
                .andReturn().getResponse().getContentAsString();
        long classId = json.readTree(created).get("id").asLong();
        mvc.perform(post("/api/admin/classes").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gradeLevel\":\"Grade 7\",\"sectionName\":\"Mabini\",\"schoolYear\":\"" + schoolYear + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/classes")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + classId + ")].label").value("Grade 7 - Mabini"));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"class-student@test.local\",\"password\":\"Password1\",\"firstName\":\"Class\",\"lastName\":\"Student\"," +
                        "\"role\":\"STUDENT\",\"schoolId\":\"LRN-CLASS-1\",\"classSectionId\":" + classId + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user.classSectionId").value(classId))
                .andExpect(jsonPath("$.user.gradeSection").value("Grade 7 - Mabini"));
        String teacher = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"class-adviser@test.local\",\"password\":\"Password1\",\"firstName\":\"Class\",\"lastName\":\"Adviser\"," +
                        "\"role\":\"TEACHER\",\"schoolId\":\"T-CLASS-1\",\"department\":\"English\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long teacherId = json.readTree(teacher).get("user").get("borrowerId").asLong();
        mvc.perform(put("/api/admin/classes/" + classId + "/adviser").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"teacherBorrowerId\":" + teacherId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.adviserName").value("Class Adviser"));
        mvc.perform(patch("/api/admin/classes/" + classId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gradeLevel\":\"Grade 7\",\"sectionName\":\"Mabini\",\"schoolYear\":\"" + schoolYear + "\",\"active\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanManagePredeterminedSubjectsAndAssignThemToBooks() throws Exception {
        String token = adminToken();
        mvc.perform(get("/api/admin/subjects").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.name=='Fiction')].active").value(true));
        mvc.perform(post("/api/admin/subjects").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Fiction\"}"))
                .andExpect(status().isConflict());
        String created = mvc.perform(post("/api/admin/subjects").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Research Reports\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Research Reports"))
                .andReturn().getResponse().getContentAsString();
        long subjectId = json.readTree(created).get("id").asLong();
        mvc.perform(post("/api/admin/catalog/books").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Unknown Shelf\",\"subject\":\"Robbers and outlaws\"}"))
                .andExpect(status().isBadRequest());
        String book = mvc.perform(post("/api/admin/catalog/books").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Known Shelf\",\"subject\":\"Research Reports\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.subject").value("Research Reports"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(patch("/api/admin/subjects/" + subjectId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Research Files\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/catalog/books?q=Known Shelf").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].subject").value("Research Files"));
        mvc.perform(get("/api/opac/books?q=Known")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(json.readTree(book).get("id").asLong()));
    }

    private String adminToken() throws Exception {
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.local\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(login).get("token").asText();
    }

    private long createClassAndAssign(String token, long teacherId, String grade, String section, String schoolYear) throws Exception {
        String response = mvc.perform(post("/api/admin/classes").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gradeLevel\":\"" + grade + "\",\"sectionName\":\"" + section +
                        "\",\"schoolYear\":\"" + schoolYear + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long classId = json.readTree(response).get("id").asLong();
        mvc.perform(put("/api/admin/classes/" + classId + "/adviser").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"teacherBorrowerId\":" + teacherId + "}"))
                .andExpect(status().isOk());
        return classId;
    }
}
