package cn.courtside.server;

import static org.junit.jupiter.api.Assertions.*;

import cn.courtside.shared.Models.*;
import cn.courtside.shared.Scoring;
import org.junit.jupiter.api.Test;

class ScoreValidationTest {
  final Rules rules = new Rules();

  ScoreSession completedGame() {
    ScoreSession s = new ScoreSession();
    for (int i = 0; i < 21; i++) Scoring.add(s, rules, 0);
    Scoring.confirmGame(s, rules);
    return s;
  }

  @Test
  void acceptsLegalSnapshotsThroughScoringUndoGameEndAndRetirement() {
    ScoreSession s = new ScoreSession();
    ScoreValidation.snapshot(s, rules);
    Scoring.add(s, rules, 1);
    ScoreValidation.snapshot(s, rules);
    Scoring.undo(s);
    for (int i = 0; i < 21; i++) Scoring.add(s, rules, 0);
    ScoreValidation.snapshot(s, rules); // Final point awaits local game confirmation.
    Scoring.confirmGame(s, rules);
    ScoreValidation.snapshot(s, rules);
    Scoring.add(s, rules, 1);
    s.retirementWinner = 0;
    s.retirementReason = "伤病弃权";
    ScoreValidation.snapshot(s, rules);
  }

  @Test
  void rejectsNullGamesIllegalRalliesMismatchedScoresAndExtraPoints() {
    ScoreSession s = new ScoreSession();
    s.games.add(null);
    assertThrows(ApiError.class, () -> ScoreValidation.snapshot(s, rules));
    s.games.clear();
    s.rallies.add(99);
    assertThrows(ApiError.class, () -> ScoreValidation.snapshot(s, rules));
    s.rallies.set(0, null);
    assertThrows(ApiError.class, () -> ScoreValidation.snapshot(s, rules));
    s.rallies.clear();
    for (int i = 0; i < 22; i++) s.rallies.add(0);
    assertThrows(ApiError.class, () -> ScoreValidation.snapshot(s, rules));
    ScoreSession mismatch = completedGame();
    mismatch.games.get(0).a = 20;
    assertThrows(IllegalArgumentException.class, () -> ScoreValidation.snapshot(mismatch, rules));
  }

  @Test
  void acceptsCompleteUnsignedSnapshotButRejectsPlayAfterMatchEnd() {
    ScoreSession s = completedGame();
    for (int i = 0; i < 21; i++) Scoring.add(s, rules, 0);
    Scoring.confirmGame(s, rules);
    ScoreValidation.snapshot(s, rules);
    s.rallies.add(1);
    assertThrows(ApiError.class, () -> ScoreValidation.snapshot(s, rules));
  }

  @Test
  void correctionPreservesTrajectoriesAndRequiresReasonForScoreChanges() {
    ScoreSession old = completedGame(), s = completedGame();
    s.correctionRound = 1;
    ScoreValidation.correction(s, old); // Unchanged re-submission remains valid.
    Game g = s.games.get(0);
    g.a = 0;
    g.b = 21;
    assertThrows(ApiError.class, () -> ScoreValidation.correction(s, old));
    g.corrected = true;
    g.correctionNote = "核对后更正胜方";
    ScoreValidation.correction(s, old);
    ScoreValidation.snapshot(s, rules);
    g.rallies.clear();
    assertThrows(ApiError.class, () -> ScoreValidation.correction(s, old));
    s.games.clear();
    assertThrows(ApiError.class, () -> ScoreValidation.correction(s, old));
  }
}
