package cn.courtside.server;

import static org.junit.jupiter.api.Assertions.*;

import cn.courtside.shared.Models.*;
import cn.courtside.shared.Scoring;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:workflow;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "courtside.seed-demo=true"
    })
class WorkflowTest {
  @Autowired TestRestTemplate http;
  @Autowired ObjectMapper json;
  Login org, ref, other;

  ResponseEntity<JsonNode> call(String method, String path, Object body, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) headers.setBearerAuth(token);
    return http.exchange(
        "/api" + path, HttpMethod.valueOf(method), new HttpEntity<>(body, headers), JsonNode.class);
  }

  <T> T ok(String method, String path, Object body, String token, Class<T> cls) {
    var response = call(method, path, body, token);
    assertEquals(200, response.getStatusCode().value(), response.toString());
    return json.convertValue(response.getBody(), cls);
  }

  Login login(String username) {
    return ok(
        "POST",
        "/auth/login",
        Map.of("username", username, "password", "Courtside2026!"),
        null,
        Login.class);
  }

  @BeforeEach
  void setup() {
    org = login("organizer");
    ref = login("referee");
    other = login("referee2");
  }

  Match fixture() {
    Rules rules = new Rules();
    rules.target = 3;
    rules.cap = 5;
    rules.gamesToWin = 1;
    Tournament t =
        ok(
            "POST",
            "/tournaments",
            Map.of("name", "接口测试赛事", "date", "2026-10-20", "location", "测试馆", "rules", rules),
            org.token,
            Tournament.class);
    ok(
        "POST",
        "/tournaments/" + t.id + "/members",
        Map.of("userId", ref.user.id),
        org.token,
        User.class);
    Entrant
        a =
            ok(
                "POST",
                "/tournaments/" + t.id + "/entrants",
                Map.of("name", "蓝方"),
                org.token,
                Entrant.class),
        b =
            ok(
                "POST",
                "/tournaments/" + t.id + "/entrants",
                Map.of("name", "红方"),
                org.token,
                Entrant.class);
    Court c =
        ok(
            "POST",
            "/tournaments/" + t.id + "/courts",
            Map.of("name", "一号场地"),
            org.token,
            Court.class);
    Match m =
        ok(
            "POST",
            "/tournaments/" + t.id + "/matches",
            Map.of(
                "sideAId",
                a.id,
                "sideBId",
                b.id,
                "courtId",
                c.id,
                "refereeId",
                ref.user.id,
                "startsAt",
                "2026-10-20T06:00:00Z",
                "endsAt",
                "2026-10-20T07:00:00Z"),
            org.token,
            Match.class);
    return ok("POST", "/matches/" + m.id + "/publish", Map.of(), org.token, Match.class);
  }

  ScoreSession completed(Match m) {
    ScoreSession s = new ScoreSession();
    s.matchId = m.id;
    s.ownerId = ref.user.id;
    s.deviceId = "test-device";
    s.sessionId = m.sessionId;
    s.submissionId = UUID.randomUUID().toString();
    s.correctionRound = m.correctionRound;
    for (int i = 0; i < 3; i++) Scoring.add(s, m.rules, 0);
    Scoring.confirmGame(s, m.rules);
    s.aConfirmed = true;
    s.bConfirmed = true;
    s.submitted = true;
    return s;
  }

  @Test
  void fullWorkflowRetryReturnAndConfirm() {
    Match m = fixture();
    String p = "/matches/" + m.id;
    assertEquals(
        403,
        call("POST", p + "/accept", Map.of("scheduleVersion", 1), other.token)
            .getStatusCode()
            .value());
    ok("POST", p + "/accept", Map.of("scheduleVersion", 1), ref.token, Match.class);
    m =
        ok(
            "POST",
            p + "/start",
            Map.of("scheduleVersion", 1, "deviceId", "test-device"),
            ref.token,
            Match.class);
    assertEquals(
        409,
        call(
                "POST",
                p + "/start",
                Map.of("scheduleVersion", 1, "deviceId", "other-device"),
                ref.token)
            .getStatusCode()
            .value());
    ScoreSession s = completed(m);
    Submission first = ok("POST", p + "/submissions", s, ref.token, Submission.class);
    Submission retry = ok("POST", p + "/submissions", s, ref.token, Submission.class);
    assertEquals(first.id, retry.id);
    s.aConfirmed = false;
    assertEquals(409, call("POST", p + "/submissions", s, ref.token).getStatusCode().value());
    s.aConfirmed = true;
    assertEquals(
        403, call("GET", "/submissions/" + first.id, null, other.token).getStatusCode().value());
    ok(
        "POST",
        "/submissions/" + first.id + "/return",
        Map.of("reason", "请核对蓝方局分"),
        org.token,
        Submission.class);
    assertEquals(first.id, ok("POST", p + "/submissions", s, ref.token, Submission.class).id);
    assertEquals(409, call("PUT", p + "/progress", s, ref.token).getStatusCode().value());
    m = ok("GET", p, null, ref.token, Match.class);
    s.correctionRound = m.correctionRound;
    s.submissionId = UUID.randomUUID().toString();
    Submission second = ok("POST", p + "/submissions", s, ref.token, Submission.class);
    ok("POST", "/submissions/" + second.id + "/confirm", Map.of(), org.token, Submission.class);
    assertEquals("FINISHED", ok("GET", p, null, ref.token, Match.class).status);
    assertEquals(409, call("PUT", p + "/progress", s, ref.token).getStatusCode().value());
    Workspace w =
        ok(
            "GET",
            "/tournaments/" + m.tournamentId + "/workspace",
            null,
            org.token,
            Workspace.class);
    assertEquals(2, w.submissions.size());
    assertEquals(1, w.submissions.stream().filter(x -> x.status.equals("CONFIRMED")).count());
  }

  @Test
  void scheduleChangesConflictAndPermissions() {
    Match m = fixture();
    String p = "/matches/" + m.id;
    Match duplicate =
        ok("POST", "/tournaments/" + m.tournamentId + "/matches", m, org.token, Match.class);
    assertEquals(
        409,
        call("POST", "/matches/" + duplicate.id + "/publish", Map.of(), org.token)
            .getStatusCode()
            .value());
    ok("POST", p + "/accept", Map.of("scheduleVersion", 1), ref.token, Match.class);
    m.startsAt = "2026-10-20T08:00:00Z";
    m.endsAt = "2026-10-20T09:00:00Z";
    Match edited = ok("PUT", p, m, org.token, Match.class);
    assertEquals(2, edited.scheduleVersion);
    assertEquals("PUBLISHED", edited.status);
    assertEquals(
        409,
        call(
                "POST",
                p + "/start",
                Map.of("scheduleVersion", 1, "deviceId", "test-device"),
                ref.token)
            .getStatusCode()
            .value());
    assertEquals(
        403,
        call("GET", "/tournaments/" + m.tournamentId + "/workspace", null, other.token)
            .getStatusCode()
            .value());
    assertEquals(401, call("GET", "/me/matches", null, null).getStatusCode().value());
  }

  @Test
  void controlledAccountsAndDraftDeletion() {
    String name = "ref_" + UUID.randomUUID().toString().substring(0, 8);
    Map<String, String> account =
        Map.of("username", name, "name", "新增裁判", "password", "NewReferee2026!");
    assertEquals(403, call("POST", "/users/referees", account, ref.token).getStatusCode().value());
    User created = ok("POST", "/users/referees", account, org.token, User.class);
    assertEquals("REFEREE", created.role);
    assertEquals(409, call("POST", "/users/referees", account, org.token).getStatusCode().value());
    assertEquals(
        200,
        call("POST", "/auth/login", Map.of("username", name, "password", "NewReferee2026!"), null)
            .getStatusCode()
            .value());
    Match m = fixture();
    assertEquals(409, call("DELETE", "/matches/" + m.id, null, org.token).getStatusCode().value());
    Match draft =
        ok("POST", "/tournaments/" + m.tournamentId + "/matches", m, org.token, Match.class);
    assertEquals(
        200, call("DELETE", "/matches/" + draft.id, null, org.token).getStatusCode().value());
    assertEquals(404, call("GET", "/matches/" + draft.id, null, org.token).getStatusCode().value());
    assertEquals(
        409,
        call("PUT", "/tournaments/" + m.tournamentId, Map.of("status", "ARCHIVED"), org.token)
            .getStatusCode()
            .value());
  }
}
