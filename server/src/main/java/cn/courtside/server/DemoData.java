package cn.courtside.server;

import cn.courtside.shared.Models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
class DemoData implements CommandLineRunner {
  final Store db;
  final Auth auth;
  final CompetitionService service;
  final ObjectMapper json;

  @Value("${courtside.seed-demo:true}")
  boolean enabled;

  @Value("${BOOTSTRAP_USERNAME:}")
  String bootstrapUsername;

  @Value("${BOOTSTRAP_PASSWORD:}")
  String bootstrapPassword;

  DemoData(Store db, Auth auth, CompetitionService service, ObjectMapper json) {
    this.db = db;
    this.auth = auth;
    this.service = service;
    this.json = json;
  }

  @Override
  public void run(String... args) {
    if (!db.list("users", null, Auth.Account.class).isEmpty()) return;
    if (!enabled) {
      if (!bootstrapUsername.isBlank() && !bootstrapPassword.isBlank())
        auth.create(bootstrapUsername, "赛事管理员", "ORGANIZER", bootstrapPassword);
      return;
    }
    auth.create("organizer", "赛事组 · 林老师", "ORGANIZER", "Courtside2026!");
    auth.create("referee", "裁判 · 曹澍", "REFEREE", "Courtside2026!");
    auth.create("referee2", "裁判 · 陈晨", "REFEREE", "Courtside2026!");
    User org = db.list("users", "organizer", Auth.Account.class).get(0).user;
    List<User> refs = service.users(org);
    Tournament t =
        service.createTournament(
            json.valueToTree(
                Map.of(
                    "name",
                    "2026 校园羽毛球邀请赛",
                    "location",
                    "远望体育馆",
                    "date",
                    LocalDate.now(ZoneId.of("Asia/Shanghai")).toString(),
                    "description",
                    "在球场相遇，用每一分认真交锋。单打邀请赛 · 赛事组统一编排。")),
            org);
    for (User ref : refs)
      service.resource(t.id, "members", null, json.valueToTree(Map.of("userId", ref.id)), org);
    String[] names = {"林一鸣", "周知远", "许嘉宁", "陈星野", "沈清越", "江屿", "陆亦辰", "程予安"};
    List<Entrant> entrants = new ArrayList<>();
    for (int i = 0; i < names.length; i++)
      entrants.add(
          (Entrant)
              service.resource(
                  t.id,
                  "entrants",
                  null,
                  json.valueToTree(
                      Map.of("name", names[i], "unit", i % 2 == 0 ? "计算机学院" : "信息与电子学院")),
                  org));
    List<Court> courts = new ArrayList<>();
    for (int i = 1; i <= 2; i++)
      courts.add(
          (Court)
              service.resource(
                  t.id,
                  "courts",
                  null,
                  json.valueToTree(Map.of("name", String.format("%02d 号场地", i))),
                  org));
    for (int i = 0; i < 4; i++) {
      Instant start =
          LocalDate.now(ZoneId.of("Asia/Shanghai"))
              .atTime(14 + i / 2, 0)
              .atZone(ZoneId.of("Asia/Shanghai"))
              .toInstant();
      Match m =
          service.createMatch(
              t.id,
              json.valueToTree(
                  Map.of(
                      "sideAId",
                      entrants.get(i * 2).id,
                      "sideBId",
                      entrants.get(i * 2 + 1).id,
                      "courtId",
                      courts.get(i % 2).id,
                      "refereeId",
                      refs.get(i % 2).id,
                      "startsAt",
                      start.toString(),
                      "endsAt",
                      start.plusSeconds(2700).toString(),
                      "round",
                      "八强赛 · 第 " + (i + 1) + " 场")),
              org);
      service.action(m.id, "publish", json.createObjectNode(), org);
    }
  }
}
