package cn.courtside.shared;

import java.util.ArrayList;
import java.util.List;

/** Wire models shared by both Android clients and the server. */
public final class Models {
  private Models() {}

  public static class Rules {
    public int gamesToWin = 2;
    public int target = 21;
    public int lead = 2;
    public int cap = 30;
  }

  public static class User {
    public String id = "", username = "", name = "", role = "REFEREE";
  }

  public static class Login {
    public String token = "";
    public User user = new User();
  }

  public static class Tournament {
    public String id = "",
        name = "",
        location = "",
        date = "",
        description = "",
        ownerId = "",
        status = "ACTIVE";
    public Rules rules = new Rules();
  }

  public static class Entrant {
    public String id = "", tournamentId = "", name = "", unit = "";
  }

  public static class Court {
    public String id = "", tournamentId = "", name = "";
  }

  public static class Match {
    public String id = "", tournamentId = "", tournamentName = "", round = "小组对阵";
    public String sideAId = "", sideBId = "", sideA = "", sideB = "", unitA = "", unitB = "";
    public String courtId = "",
        court = "",
        refereeId = "",
        refereeName = "",
        startsAt = "",
        endsAt = "";
    public String status = "DRAFT",
        sessionId = "",
        deviceId = "",
        returnReason = "",
        cancelReason = "";
    public String currentSubmissionId = "", updatedAt = "";
    public int scheduleVersion = 1, correctionRound = 0;
    public Rules rules = new Rules();
    public ScoreSession progress;
  }

  public static class Game {
    public List<Integer> rallies = new ArrayList<>();
    public int a, b;
    public boolean corrected = false;
    public String correctionNote = "";
  }

  public static class ScoreSession {
    public String matchId = "",
        ownerId = "",
        sessionId = "",
        deviceId = "",
        submissionId = "",
        retirementReason = "";
    public int correctionRound = 0, initialServer = 0, retirementWinner = -1, intervalGame = -1;
    public long localSeq = 0, updatedAt = 0, breakUntil = 0;
    public boolean aConfirmed = false, bConfirmed = false, swapped = false, submitted = false;
    public List<Game> games = new ArrayList<>();
    public List<Integer> rallies = new ArrayList<>();
  }

  public static class Submission {
    public String id = "",
        matchId = "",
        tournamentId = "",
        refereeId = "",
        status = "PENDING_REVIEW";
    public String createdAt = "", reviewedAt = "", reviewReason = "";
    public int winner = -1;
    public ScoreSession score = new ScoreSession();
  }

  public static class Audit {
    public String id = "",
        tournamentId = "",
        matchId = "",
        actor = "",
        action = "",
        detail = "",
        at = "";
  }

  public static class Workspace {
    public Tournament tournament = new Tournament();
    public List<Entrant> entrants = new ArrayList<>();
    public List<Court> courts = new ArrayList<>();
    public List<User> referees = new ArrayList<>();
    public List<Match> matches = new ArrayList<>();
    public List<Submission> submissions = new ArrayList<>();
    public List<Audit> audit = new ArrayList<>();
  }

  public static class Feed {
    public List<Tournament> tournaments = new ArrayList<>();
    public List<Match> matches = new ArrayList<>();
    public List<Submission> submissions = new ArrayList<>();
  }
}
