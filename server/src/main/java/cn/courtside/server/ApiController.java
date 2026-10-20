package cn.courtside.server;

import cn.courtside.shared.Models.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
  final CompetitionService service;
  final Auth auth;

  ApiController(CompetitionService service, Auth auth) {
    this.service = service;
    this.auth = auth;
  }

  User user(HttpServletRequest r) {
    return (User) r.getAttribute("user");
  }

  @GetMapping("/health")
  Object health() {
    return Map.of("status", "UP", "service", "courtside", "version", "1.0.0");
  }

  @PostMapping("/auth/login")
  Login login(@RequestBody JsonNode b, HttpServletRequest r) {
    return auth.login(
        b.path("username").asText(""), b.path("password").asText(""), r.getRemoteAddr());
  }

  @GetMapping("/me")
  User me(HttpServletRequest r) {
    return user(r);
  }

  @GetMapping("/users/referees")
  Object users(HttpServletRequest r) {
    return service.users(user(r));
  }

  @PostMapping("/users/referees")
  Object referee(@RequestBody JsonNode b, HttpServletRequest r) {
    return service.createReferee(b, user(r), auth);
  }

  @GetMapping("/tournaments")
  Object tournaments(HttpServletRequest r) {
    return service.tournaments(user(r));
  }

  @PostMapping("/tournaments")
  Object tournament(@RequestBody JsonNode b, HttpServletRequest r) {
    return service.createTournament(b, user(r));
  }

  @PutMapping("/tournaments/{id}")
  Object update(@PathVariable String id, @RequestBody JsonNode b, HttpServletRequest r) {
    return service.updateTournament(id, b, user(r));
  }

  @GetMapping("/tournaments/{id}/workspace")
  Object workspace(@PathVariable String id, HttpServletRequest r) {
    return service.workspace(id, user(r));
  }

  @PostMapping("/tournaments/{id}/{kind:entrants|courts|members}")
  Object resource(
      @PathVariable String id,
      @PathVariable String kind,
      @RequestBody JsonNode b,
      HttpServletRequest r) {
    return service.resource(id, kind, null, b, user(r));
  }

  @PutMapping("/tournaments/{id}/{kind:entrants|courts}/{rid}")
  Object resourceEdit(
      @PathVariable String id,
      @PathVariable String kind,
      @PathVariable String rid,
      @RequestBody JsonNode b,
      HttpServletRequest r) {
    return service.resource(id, kind, rid, b, user(r));
  }

  @PostMapping("/tournaments/{id}/matches")
  Object createMatch(@PathVariable String id, @RequestBody JsonNode b, HttpServletRequest r) {
    return service.createMatch(id, b, user(r));
  }

  @PutMapping("/matches/{id}")
  Object edit(@PathVariable String id, @RequestBody JsonNode b, HttpServletRequest r) {
    return service.editMatch(id, b, user(r));
  }

  @DeleteMapping("/matches/{id}")
  Object delete(@PathVariable String id, HttpServletRequest r) {
    service.deleteDraft(id, user(r));
    return Map.of("deleted", true);
  }

  @GetMapping("/me/matches")
  Object feed(HttpServletRequest r) {
    return service.feed(user(r));
  }

  @GetMapping("/matches/{id}")
  Object match(@PathVariable String id, HttpServletRequest r) {
    return service.getMatch(id, user(r));
  }

  @PostMapping("/matches/{id}/{action:publish|accept|start|cancel}")
  Object action(
      @PathVariable String id,
      @PathVariable String action,
      @RequestBody JsonNode b,
      HttpServletRequest r) {
    return service.action(id, action, b, user(r));
  }

  @PutMapping("/matches/{id}/progress")
  Object progress(@PathVariable String id, @RequestBody ScoreSession s, HttpServletRequest r) {
    return service.progress(id, s, user(r));
  }

  @PostMapping("/matches/{id}/submissions")
  Object submit(@PathVariable String id, @RequestBody ScoreSession s, HttpServletRequest r) {
    return service.submit(id, s, user(r));
  }

  @GetMapping("/submissions/{id}")
  Object receipt(@PathVariable String id, HttpServletRequest r) {
    return service.receipt(id, user(r));
  }

  @PostMapping("/submissions/{id}/{action:confirm|return}")
  Object review(
      @PathVariable String id,
      @PathVariable String action,
      @RequestBody JsonNode b,
      HttpServletRequest r) {
    return service.review(id, action, b, user(r));
  }
}
