package cn.courtside.shared;

import static org.junit.Assert.*;

import cn.courtside.shared.Models.*;
import org.junit.Test;

public class ScoringTest {
  final Rules r = new Rules();

  @Test
  public void deuceAndCap() {
    assertFalse(Scoring.gameOver(21, 20, r));
    assertTrue(Scoring.gameOver(22, 20, r));
    assertFalse(Scoring.gameOver(29, 29, r));
    assertTrue(Scoring.gameOver(30, 29, r));
  }

  @Test
  public void undoKeepsSequenceMonotonicAndRestoresServer() {
    ScoreSession s = new ScoreSession();
    s.initialServer = 1;
    Scoring.add(s, r, 0);
    Scoring.add(s, r, 1);
    assertEquals(1, Scoring.server(s));
    Scoring.undo(s);
    assertEquals(3, s.localSeq);
    assertEquals(0, Scoring.server(s));
    Scoring.undo(s);
    assertEquals(1, Scoring.server(s));
  }

  @Test
  public void gameAndMatchPoint() {
    ScoreSession s = new ScoreSession();
    for (int i = 0; i < 20; i++) Scoring.add(s, r, 0);
    assertTrue(Scoring.gamePoint(s, r, 0));
    Scoring.add(s, r, 0);
    Scoring.confirmGame(s, r);
    assertEquals(1, Scoring.wins(s, 0));
    assertEquals(0, Scoring.server(s));
    for (int i = 0; i < 21; i++) Scoring.add(s, r, 0);
    Scoring.confirmGame(s, r);
    assertEquals(0, Scoring.winner(s, r));
    s.aConfirmed = true;
    s.bConfirmed = true;
    Scoring.validateResult(s, r);
    assertThrows(IllegalArgumentException.class, () -> Scoring.add(s, r, 0));
  }

  @Test
  public void extraPointAndFalseScoresRejected() {
    ScoreSession s = new ScoreSession();
    Game g = new Game();
    for (int i = 0; i < 22; i++) g.rallies.add(0);
    g.a = 22;
    s.games.add(g);
    assertThrows(IllegalArgumentException.class, () -> Scoring.validateResult(s, r));
  }

  @Test
  public void cannotUndoSealedGame() {
    ScoreSession s = new ScoreSession();
    for (int i = 0; i < 21; i++) Scoring.add(s, r, 0);
    Scoring.confirmGame(s, r);
    assertThrows(IllegalArgumentException.class, () -> Scoring.undo(s));
  }

  @Test
  public void correctionsRequireNewRoundAndReason() {
    ScoreSession s = new ScoreSession();
    Game g = new Game();
    g.corrected = true;
    g.a = 21;
    g.b = 19;
    g.correctionNote = "核对记录";
    s.games.add(g);
    s.games.add(g);
    s.aConfirmed = true;
    s.bConfirmed = true;
    assertThrows(IllegalArgumentException.class, () -> Scoring.validateResult(s, r));
    s.correctionRound = 1;
    Scoring.validateResult(s, r);
    g.a = 25;
    g.b = 19;
    assertThrows(IllegalArgumentException.class, () -> Scoring.validateResult(s, r));
  }
}
