package cn.courtside.server;

import static cn.courtside.server.ApiError.check;

import cn.courtside.shared.Models.*;
import cn.courtside.shared.Scoring;
import java.util.List;

/** Validation at the server boundary; scoring operations remain in shared Scoring. */
final class ScoreValidation {
  private ScoreValidation() {}

  static void snapshot(ScoreSession s, Rules r) {
    check(s != null && s.games != null && s.rallies != null && s.localSeq >= 0,
        400, "VALIDATION", "比赛记录格式无效");
    Scoring.validateRules(r);
    check(s.initialServer == 0 || s.initialServer == 1, 400, "VALIDATION", "初始发球方无效");
    check(s.games.size() <= r.gamesToWin * 2 - 1, 400, "VALIDATION", "局数超出比赛规则范围");
    int aw = 0, bw = 0;
    for (Game g : s.games) {
      check(g != null, 400, "VALIDATION", "局记录不能为空");
      check(aw < r.gamesToWin && bw < r.gamesToWin, 400, "VALIDATION", "整场结束后存在多余局");
      // Validate each sealed game with the existing rules, without requiring the
      // ongoing match or the players' result confirmations to be complete.
      Rules singleGame = new Rules();
      singleGame.gamesToWin = 1;
      singleGame.target = r.target;
      singleGame.lead = r.lead;
      singleGame.cap = r.cap;
      ScoreSession sealed = new ScoreSession();
      sealed.games.add(g);
      sealed.correctionRound = s.correctionRound;
      sealed.aConfirmed = sealed.bConfirmed = true;
      Scoring.validateResult(sealed, singleGame);
      if (g.a > g.b) aw++;
      else bw++;
    }
    rallies(s.rallies, r);
    check((aw < r.gamesToWin && bw < r.gamesToWin) || s.rallies.isEmpty(),
        400, "VALIDATION", "整场结束后存在额外得分");
    check(s.retirementWinner >= -1 && s.retirementWinner <= 1,
        400, "VALIDATION", "弃权胜方无效");
    if (s.retirementWinner >= 0) {
      check(s.retirementReason != null && !s.retirementReason.isBlank(),
          400, "VALIDATION", "请填写弃权原因");
      check(aw < r.gamesToWin && bw < r.gamesToWin,
          400, "VALIDATION", "已正常完赛不能再提交弃权");
    }
  }

  private static void rallies(List<Integer> rallies, Rules r) {
    check(rallies.size() <= r.cap * 2, 400, "VALIDATION", "逐分记录长度无效");
    int a = 0, b = 0;
    for (Integer side : rallies) {
      check(side != null && (side == 0 || side == 1), 400, "VALIDATION", "逐分记录无效");
      check(!Scoring.gameOver(a, b, r), 400, "VALIDATION", "本局结束后存在额外得分");
      if (side == 0) a++;
      else b++;
    }
  }

  static void correction(ScoreSession s, ScoreSession previous) {
    check(s.games != null && s.rallies != null
            && s.games.size() == previous.games.size()
            && s.rallies.equals(previous.rallies),
        400, "VALIDATION", "更正时必须保留原始局数与逐分记录");
    for (int i = 0; i < previous.games.size(); i++) {
      Game g = s.games.get(i), old = previous.games.get(i);
      check(g != null && g.rallies != null && g.rallies.equals(old.rallies),
          400, "VALIDATION", "更正时必须保留原始逐分记录");
      if (g.a != old.a || g.b != old.b)
        check(g.corrected && g.correctionNote != null && !g.correctionNote.isBlank(),
            400, "VALIDATION", "修改局分必须标记更正并填写原因");
    }
  }
}
