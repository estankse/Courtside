package cn.courtside.shared;

import cn.courtside.shared.Models.*;
import java.util.ArrayList;
import java.util.List;

/** Pure scoring rules, with no network or Android dependency. A is always 0, B is 1. */
public final class Scoring {
  private Scoring() {}

  public static int points(List<Integer> rallies, int side) {
    int value = 0;
    for (Integer rally : rallies) if (rally != null && rally == side) value++;
    return value;
  }

  public static void validateRules(Rules r) {
    require(r != null, "缺少比赛规则");
    require(r.gamesToWin >= 1 && r.gamesToWin <= 3, "获胜局数需为 1 至 3");
    require(
        r.target >= 3
            && r.target <= 30
            && r.lead >= 1
            && r.lead <= 3
            && r.cap >= r.target
            && r.cap <= 40,
        "计分规则参数无效");
  }

  public static boolean gameOver(int a, int b, Rules r) {
    return a != b
        && (Math.max(a, b) >= r.cap || (Math.max(a, b) >= r.target && Math.abs(a - b) >= r.lead));
  }

  public static int wins(ScoreSession s, int side) {
    int wins = 0;
    for (Game g : s.games) if ((g.a > g.b ? 0 : 1) == side) wins++;
    return wins;
  }

  public static int winner(ScoreSession s, Rules r) {
    if (s.retirementWinner >= 0) return s.retirementWinner;
    if (wins(s, 0) >= r.gamesToWin) return 0;
    if (wins(s, 1) >= r.gamesToWin) return 1;
    return -1;
  }

  public static int server(ScoreSession s) {
    if (!s.rallies.isEmpty()) return s.rallies.get(s.rallies.size() - 1);
    if (!s.games.isEmpty()) {
      Game g = s.games.get(s.games.size() - 1);
      return g.a > g.b ? 0 : 1;
    }
    return s.initialServer;
  }

  public static boolean gamePoint(ScoreSession s, Rules r, int side) {
    int a = points(s.rallies, 0), b = points(s.rallies, 1);
    if (gameOver(a, b, r) || winner(s, r) >= 0) return false;
    return gameOver(a + (side == 0 ? 1 : 0), b + (side == 1 ? 1 : 0), r);
  }

  public static void add(ScoreSession s, Rules r, int side) {
    require(side == 0 || side == 1, "得分方无效");
    require(
        !s.submitted
            && winner(s, r) < 0
            && !gameOver(points(s.rallies, 0), points(s.rallies, 1), r),
        "请先确认本局结果");
    s.rallies.add(side);
    s.localSeq++;
    s.updatedAt = System.currentTimeMillis();
    s.aConfirmed = false;
    s.bConfirmed = false;
  }

  public static void undo(ScoreSession s) {
    require(!s.submitted && !s.rallies.isEmpty(), "本局没有可撤销的得分");
    s.rallies.remove(s.rallies.size() - 1);
    s.localSeq++;
    s.breakUntil = 0;
    s.updatedAt = System.currentTimeMillis();
  }

  public static void confirmGame(ScoreSession s, Rules r) {
    int a = points(s.rallies, 0), b = points(s.rallies, 1);
    require(!s.submitted && gameOver(a, b, r), "本局尚未结束");
    Game g = new Game();
    g.rallies = new ArrayList<>(s.rallies);
    g.a = a;
    g.b = b;
    s.games.add(g);
    s.rallies.clear();
    s.localSeq++;
    s.updatedAt = System.currentTimeMillis();
  }

  public static void validateResult(ScoreSession s, Rules r) {
    validateRules(r);
    require(s != null && s.games != null && s.rallies != null && s.games.size() <= 5, "比赛记录格式无效");
    require(s.initialServer == 0 || s.initialServer == 1, "初始发球方无效");
    int aw = 0, bw = 0;
    for (Game g : s.games) {
      require(g != null && g.rallies != null && g.correctionNote != null, "局记录格式无效");
      require(aw < r.gamesToWin && bw < r.gamesToWin, "整场结束后存在多余局");
      if (g.corrected) {
        require(s.correctionRound > 0 && !blank(g.correctionNote), "更正必须填写原因");
        require(
            g.a >= 0 && g.b >= 0 && g.a <= r.cap && g.b <= r.cap && gameOver(g.a, g.b, r),
            "更正局分无效");
        int wa = g.a > g.b ? g.a - 1 : g.a, wb = g.b > g.a ? g.b - 1 : g.b;
        require(!gameOver(wa, wb, r), "更正比分超过了本局结束点");
      } else {
        validateRallies(g.rallies, r, true);
        require(g.a == points(g.rallies, 0) && g.b == points(g.rallies, 1), "局分与逐分记录不一致");
      }
      if (g.a > g.b) aw++;
      else bw++;
    }
    validateRallies(s.rallies, r, false);
    require(s.retirementWinner >= -1 && s.retirementWinner <= 1, "弃权胜方无效");
    if (s.retirementWinner >= 0) {
      require(!blank(s.retirementReason), "请填写弃权原因");
      require(aw < r.gamesToWin && bw < r.gamesToWin, "已正常完赛不能再提交弃权");
    } else {
      require((aw == r.gamesToWin || bw == r.gamesToWin) && s.rallies.isEmpty(), "比赛尚未完成");
      require(s.aConfirmed && s.bConfirmed, "请完成双方赛后确认");
    }
  }

  private static void validateRallies(List<Integer> rallies, Rules r, boolean mustEnd) {
    require(rallies != null && rallies.size() <= r.cap * 2, "逐分记录长度无效");
    int a = 0, b = 0;
    for (Integer side : rallies) {
      require(side != null && (side == 0 || side == 1), "逐分记录无效");
      require(!gameOver(a, b, r), "本局结束后存在额外得分");
      if (side == 0) a++;
      else b++;
    }
    if (mustEnd) require(gameOver(a, b, r), "已确认局尚未达到结束条件");
  }

  private static void require(boolean ok, String msg) {
    if (!ok) throw new IllegalArgumentException(msg);
  }

  private static boolean blank(String value) {
    if (value == null) return true;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) return false;
    }
    return true;
  }
}
