package cn.courtside.server;

import static cn.courtside.server.ApiError.check;

import cn.courtside.shared.Models.*;
import cn.courtside.shared.Scoring;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CompetitionService {
  final Store db;

  CompetitionService(Store db) {
    this.db = db;
  }

  String text(JsonNode body, String key) {
    return body.path(key).asText("").trim();
  }

  String required(JsonNode body, String key) {
    String v = text(body, key);
    check(!v.isBlank() && v.length() <= 500, 400, "VALIDATION", "请完整填写 " + key);
    return v;
  }

  <T> T convert(JsonNode body, Class<T> cls) {
    return db.json.convertValue(body, cls);
  }

  Tournament owner(String id, User user) {
    Tournament t = db.get("tournaments", id, Tournament.class);
    check(t.ownerId.equals(user.id), 403, "FORBIDDEN", "你没有该赛事的管理权限");
    return t;
  }

  void member(String id, User u) {
    Tournament t = db.get("tournaments", id, Tournament.class);
    check(
        t.ownerId.equals(u.id) || db.exists("members", id + ":" + u.id),
        403,
        "FORBIDDEN",
        "你未加入该赛事");
  }

  void active(Tournament t) {
    check(!t.status.equals("ARCHIVED"), 409, "ARCHIVED", "赛事已归档，不能继续修改");
  }

  void audit(String tid, String mid, User u, String action, String detail) {
    Audit a = new Audit();
    a.id = Store.id();
    a.tournamentId = tid;
    a.matchId = mid;
    a.actor = u.name;
    a.action = action;
    a.detail = detail;
    a.at = Store.now();
    db.insert("audit", a.id, tid, a);
  }

  void save(Match m) {
    m.updatedAt = Store.now();
    db.save("matches", m.id, m.tournamentId, m);
  }

  public List<User> users(User u) {
    check(u.role.equals("ORGANIZER"), 403, "FORBIDDEN", "仅赛事组可以查看裁判名册");
    return db.list("users", null, Auth.Account.class).stream()
        .map(a -> a.user)
        .filter(a -> a.role.equals("REFEREE"))
        .toList();
  }

  public User createReferee(JsonNode b, User u, Auth auth) {
    check(u.role.equals("ORGANIZER"), 403, "FORBIDDEN", "仅赛事组可以创建裁判账号");
    String username = required(b, "username");
    auth.create(username, required(b, "name"), "REFEREE", required(b, "password"));
    return db.list("users", username, Auth.Account.class).get(0).user;
  }

  public void deleteDraft(String id, User u) {
    Match m = db.get("matches", id, Match.class);
    owner(m.tournamentId, u);
    db.lock(m.tournamentId);
    m = db.get("matches", id, Match.class);
    check(m.status.equals("DRAFT"), 409, "STATE_CONFLICT", "只能删除未发布的草稿");
    db.jdbc.update("DELETE FROM cs_matches WHERE id=?", id);
    audit(m.tournamentId, id, u, "删除草稿", m.sideA + " vs " + m.sideB);
  }

  public List<Tournament> tournaments(User u) {
    return db.list("tournaments", null, Tournament.class).stream()
        .filter(t -> t.ownerId.equals(u.id) || db.exists("members", t.id + ":" + u.id))
        .toList();
  }

  public Tournament createTournament(JsonNode b, User u) {
    check(u.role.equals("ORGANIZER"), 403, "FORBIDDEN", "仅赛事组可以创建赛事");
    Tournament t = convert(b, Tournament.class);
    t.name = required(b, "name");
    t.location = required(b, "location");
    t.date = required(b, "date");
    LocalDate.parse(t.date);
    Scoring.validateRules(t.rules);
    t.id = Store.id();
    t.ownerId = u.id;
    t.status = "ACTIVE";
    db.insert("tournaments", t.id, u.id, t);
    audit(t.id, "", u, "创建赛事", t.name);
    return t;
  }

  public Tournament updateTournament(String id, JsonNode b, User u) {
    Tournament t = owner(id, u);
    db.lock(id);
    t = owner(id, u);
    active(t);
    if (text(b, "status").equals("ARCHIVED")) {
      List<Match> ms = db.list("matches", id, Match.class);
      check(
          !ms.isEmpty()
              && ms.stream().allMatch(m -> Set.of("FINISHED", "CANCELLED").contains(m.status)),
          409,
          "UNFINISHED",
          "请先完成或取消全部比赛");
      t.status = "ARCHIVED";
    } else {
      t.name = required(b, "name");
      t.location = required(b, "location");
      t.date = required(b, "date");
      LocalDate.parse(t.date);
      t.description = text(b, "description");
      if (b.has("rules")) {
        check(
            db.list("matches", id, Match.class).stream().allMatch(m -> m.status.equals("DRAFT")),
            409,
            "RULES_LOCKED",
            "已有发布比赛，规则已锁定");
        t.rules = convert(b.get("rules"), Rules.class);
        Scoring.validateRules(t.rules);
      }
    }
    db.save("tournaments", id, t.ownerId, t);
    audit(id, "", u, "更新赛事", t.name);
    return t;
  }

  public Object resource(String tid, String kind, String rid, JsonNode b, User u) {
    Tournament t = owner(tid, u);
    db.lock(tid);
    t = owner(tid, u);
    active(t);
    if (kind.equals("members")) {
      User ref = db.get("users", required(b, "userId"), Auth.Account.class).user;
      check(ref.role.equals("REFEREE"), 400, "VALIDATION", "请选择裁判账号");
      String key = tid + ":" + ref.id;
      if (!db.exists("members", key)) db.insert("members", key, tid, ref);
      return ref;
    }
    if (kind.equals("entrants")) {
      Entrant e = new Entrant();
      if (rid != null) {
        e = db.get(kind, rid, Entrant.class);
        check(e.tournamentId.equals(tid), 403, "FORBIDDEN", "参赛者不属于该赛事");
      }
      e.name = required(b, "name");
      e.unit = text(b, "unit");
      e.tournamentId = tid;
      if (rid == null) {
        e.id = Store.id();
        db.insert(kind, e.id, tid, e);
      } else {
        db.save(kind, e.id, tid, e);
      }
      return e;
    }
    check(kind.equals("courts"), 404, "NOT_FOUND", "资源不存在");
    Court c = new Court();
    if (rid != null) {
      c = db.get(kind, rid, Court.class);
      check(c.tournamentId.equals(tid), 403, "FORBIDDEN", "场地不属于该赛事");
    }
    c.name = required(b, "name");
    c.tournamentId = tid;
    if (rid == null) {
      c.id = Store.id();
      db.insert(kind, c.id, tid, c);
    } else db.save(kind, c.id, tid, c);
    return c;
  }

  public Workspace workspace(String id, User u) {
    owner(id, u);
    Workspace w = new Workspace();
    w.tournament = db.get("tournaments", id, Tournament.class);
    w.entrants = db.list("entrants", id, Entrant.class);
    w.courts = db.list("courts", id, Court.class);
    w.referees = db.list("members", id, User.class);
    w.matches = sorted(db.list("matches", id, Match.class));
    w.submissions = db.list("submissions", id, Submission.class);
    w.audit = db.list("audit", id, Audit.class);
    return w;
  }

  List<Match> sorted(List<Match> ms) {
    return ms.stream().sorted(Comparator.comparing(m -> m.startsAt)).toList();
  }

  public Feed feed(User u) {
    Feed f = new Feed();
    f.tournaments = tournaments(u);
    f.matches =
        sorted(
            db.list("matches", null, Match.class).stream()
                .filter(m -> m.refereeId.equals(u.id) && !m.status.equals("DRAFT"))
                .toList());
    f.submissions =
        db.list("submissions", null, Submission.class).stream()
            .filter(s -> s.refereeId.equals(u.id))
            .toList();
    return f;
  }

  public Match getMatch(String id, User u) {
    Match m = db.get("matches", id, Match.class);
    check(
        m.refereeId.equals(u.id)
            || db.get("tournaments", m.tournamentId, Tournament.class).ownerId.equals(u.id),
        403,
        "FORBIDDEN",
        "无权查看该场比赛");
    return m;
  }

  void bindDetails(Match m, Tournament t) {
    check(
        java.util.stream.Stream.of(
                m.sideAId, m.sideBId, m.courtId, m.refereeId, m.startsAt, m.endsAt)
            .allMatch(v -> v != null && !v.isBlank()),
        400,
        "VALIDATION",
        "请完整填写比赛安排");
    check(!m.sideAId.equals(m.sideBId), 400, "VALIDATION", "对阵双方不能是同一人");
    Entrant a = db.get("entrants", m.sideAId, Entrant.class),
        b = db.get("entrants", m.sideBId, Entrant.class);
    Court c = db.get("courts", m.courtId, Court.class);
    check(
        a.tournamentId.equals(t.id)
            && b.tournamentId.equals(t.id)
            && c.tournamentId.equals(t.id)
            && db.exists("members", t.id + ":" + m.refereeId),
        400,
        "VALIDATION",
        "双方、场地和裁判必须属于该赛事");
    m.sideA = a.name;
    m.sideB = b.name;
    m.unitA = a.unit;
    m.unitB = b.unit;
    m.court = c.name;
    m.refereeName = db.get("users", m.refereeId, Auth.Account.class).user.name;
    m.tournamentName = t.name;
    m.rules = t.rules;
    Instant start = Instant.parse(m.startsAt), end = Instant.parse(m.endsAt);
    check(end.isAfter(start), 400, "VALIDATION", "结束时间必须晚于开始时间");
  }

  void conflicts(Match m) {
    Instant a = Instant.parse(m.startsAt), b = Instant.parse(m.endsAt);
    for (Match other : db.list("matches", m.tournamentId, Match.class)) {
      if (other.id.equals(m.id) || Set.of("DRAFT", "CANCELLED").contains(other.status)) continue;
      if (a.isBefore(Instant.parse(other.endsAt)) && b.isAfter(Instant.parse(other.startsAt))) {
        check(!m.courtId.equals(other.courtId), 409, "COURT_CONFLICT", "该时间段场地已被占用");
        check(!m.refereeId.equals(other.refereeId), 409, "REFEREE_CONFLICT", "该裁判在该时间段已有执裁任务");
        check(
            !Set.of(other.sideAId, other.sideBId).contains(m.sideAId)
                && !Set.of(other.sideAId, other.sideBId).contains(m.sideBId),
            409,
            "ENTRANT_CONFLICT",
            "参赛者在该时间段已有比赛");
      }
    }
  }

  public Match createMatch(String tid, JsonNode b, User u) {
    Tournament t = owner(tid, u);
    db.lock(tid);
    t = owner(tid, u);
    active(t);
    Match m = convert(b, Match.class);
    m.id = Store.id();
    m.tournamentId = tid;
    m.status = "DRAFT";
    m.sessionId = "";
    m.deviceId = "";
    m.progress = null;
    m.currentSubmissionId = "";
    m.scheduleVersion = 1;
    m.correctionRound = 0;
    bindDetails(m, t);
    m.updatedAt = Store.now();
    db.insert("matches", m.id, tid, m);
    audit(tid, m.id, u, "创建比赛", m.sideA + " vs " + m.sideB);
    return m;
  }

  public Match editMatch(String id, JsonNode b, User u) {
    Match m = db.get("matches", id, Match.class);
    Tournament t = owner(m.tournamentId, u);
    db.lock(t.id);
    m = db.get("matches", id, Match.class);
    active(t);
    check(
        Set.of("DRAFT", "PUBLISHED", "ACCEPTED").contains(m.status),
        409,
        "STATE_CONFLICT",
        "比赛已经开始，不能修改安排");
    check(
        b.path("scheduleVersion").asInt() == m.scheduleVersion, 409, "STALE_SCHEDULE", "安排已更新，请刷新");
    Match next = convert(b, Match.class);
    next.id = m.id;
    next.tournamentId = m.tournamentId;
    next.status = m.status.equals("DRAFT") ? "DRAFT" : "PUBLISHED";
    next.scheduleVersion = m.scheduleVersion + 1;
    next.correctionRound = 0;
    next.sessionId = "";
    next.deviceId = "";
    next.progress = null;
    next.currentSubmissionId = "";
    bindDetails(next, t);
    if (!next.status.equals("DRAFT")) conflicts(next);
    save(next);
    audit(t.id, m.id, u, "调整赛程", "安排版本 " + next.scheduleVersion);
    return next;
  }

  public Match action(String id, String action, JsonNode b, User u) {
    Match m = db.get("matches", id, Match.class);
    db.lock(m.tournamentId);
    m = db.get("matches", id, Match.class);
    Tournament t = db.get("tournaments", m.tournamentId, Tournament.class);
    active(t);
    if (action.equals("publish") || action.equals("cancel")) {
      owner(t.id, u);
      check(
          Set.of("DRAFT", "PUBLISHED", "ACCEPTED").contains(m.status),
          409,
          "STATE_CONFLICT",
          "只能处理未开始的比赛");
      if (action.equals("publish")) {
        check(m.status.equals("DRAFT"), 409, "STATE_CONFLICT", "比赛已发布");
        bindDetails(m, t);
        conflicts(m);
        m.status = "PUBLISHED";
      } else {
        m.cancelReason = required(b, "reason");
        m.status = "CANCELLED";
      }
    } else {
      check(m.refereeId.equals(u.id), 403, "FORBIDDEN", "你不是本场指定裁判");
      check(
          b.path("scheduleVersion").asInt() == m.scheduleVersion,
          409,
          "STALE_SCHEDULE",
          "赛程已更新，请刷新后重新接受");
      if (action.equals("accept")) {
        check(m.status.equals("PUBLISHED"), 409, "STATE_CONFLICT", "当前状态不能接受任务");
        m.status = "ACCEPTED";
      } else if (action.equals("start")) {
        check(m.status.equals("ACCEPTED"), 409, "STATE_CONFLICT", "请先接受任务，或从原设备继续比赛");
        m.status = "IN_PROGRESS";
        m.deviceId = required(b, "deviceId");
        m.sessionId = Store.id();
      } else throw new ApiError(404, "NOT_FOUND", "操作不存在");
    }
    save(m);
    String actionLabel =
        switch (action) {
          case "publish" -> "发布赛程";
          case "accept" -> "接受执裁";
          case "start" -> "开始比赛";
          case "cancel" -> "取消比赛";
          default -> action;
        };
    audit(t.id, m.id, u, actionLabel, m.sideA + " vs " + m.sideB);
    return m;
  }

  void session(Match m, ScoreSession s, User u) {
    check(m.refereeId.equals(u.id) && u.id.equals(s.ownerId), 403, "FORBIDDEN", "只能提交本人执裁记录");
    check(
        m.id.equals(s.matchId) && m.sessionId.equals(s.sessionId) && m.deviceId.equals(s.deviceId),
        409,
        "SESSION_CONFLICT",
        "请使用开始本场比赛的原设备");
    check(m.correctionRound == s.correctionRound, 409, "STALE_ROUND", "成绩已退回更正，请刷新后再操作");
  }

  public Match progress(String id, ScoreSession s, User u) {
    Match m = db.get("matches", id, Match.class);
    db.lock(m.tournamentId);
    m = db.get("matches", id, Match.class);
    session(m, s, u);
    check(m.status.equals("IN_PROGRESS"), 409, "STATE_CONFLICT", "比赛已提交或完成");
    check(
        s.games != null
            && s.rallies != null
            && s.localSeq >= 0
            && s.games.size() <= 5
            && s.rallies.size() <= 80,
        400,
        "VALIDATION",
        "比分数据超出范围");
    if (m.progress == null || s.localSeq > m.progress.localSeq) {
      m.progress = s;
      save(m);
    }
    return m;
  }

  public Submission submit(String id, ScoreSession score, User u) {
    check(
        score.submissionId != null
            && !score.submissionId.isBlank()
            && score.submissionId.length() <= 64,
        400,
        "VALIDATION",
        "缺少提交编号");
    Match m = db.get("matches", id, Match.class);
    db.lock(m.tournamentId);
    m = db.get("matches", id, Match.class);
    if (db.exists("submissions", score.submissionId)) {
      Submission old = db.get("submissions", score.submissionId, Submission.class);
      check(old.refereeId.equals(u.id) && old.matchId.equals(id), 403, "FORBIDDEN", "无权访问该提交");
      check(
          db.encode(old.score).equals(db.encode(score)),
          409,
          "IDEMPOTENCY_CONFLICT",
          "同一提交编号对应了不同内容");
      return old;
    }
    session(m, score, u);
    check(m.status.equals("IN_PROGRESS"), 409, "STATE_CONFLICT", "比赛当前不能提交成绩");
    Scoring.validateResult(score, m.rules);
    Submission s = new Submission();
    s.id = score.submissionId;
    s.matchId = id;
    s.tournamentId = m.tournamentId;
    s.refereeId = u.id;
    s.createdAt = Store.now();
    s.score = score;
    s.winner = Scoring.winner(score, m.rules);
    db.insert("submissions", s.id, m.tournamentId, s);
    m.currentSubmissionId = s.id;
    m.status = "PENDING_REVIEW";
    m.progress = score;
    save(m);
    audit(m.tournamentId, m.id, u, "提交成绩", m.sideA + " vs " + m.sideB);
    return s;
  }

  public Submission receipt(String id, User u) {
    Submission s = db.get("submissions", id, Submission.class);
    getMatch(s.matchId, u);
    return s;
  }

  public Submission review(String id, String action, JsonNode b, User u) {
    Submission s = db.get("submissions", id, Submission.class);
    owner(s.tournamentId, u);
    db.lock(s.tournamentId);
    s = db.get("submissions", id, Submission.class);
    Match m = db.get("matches", s.matchId, Match.class);
    check(
        m.status.equals("PENDING_REVIEW")
            && m.currentSubmissionId.equals(id)
            && s.status.equals("PENDING_REVIEW"),
        409,
        "STATE_CONFLICT",
        "该提交已核验或被更新");
    if (action.equals("confirm")) {
      s.status = "CONFIRMED";
      m.status = "FINISHED";
    } else if (action.equals("return")) {
      s.reviewReason = required(b, "reason");
      s.status = "RETURNED";
      m.status = "IN_PROGRESS";
      m.correctionRound++;
      m.returnReason = s.reviewReason;
    } else throw new ApiError(404, "NOT_FOUND", "操作不存在");
    s.reviewedAt = Store.now();
    db.save("submissions", s.id, s.tournamentId, s);
    save(m);
    audit(s.tournamentId, m.id, u, action.equals("confirm") ? "确认成绩" : "退回更正", s.reviewReason);
    return s;
  }
}
