package cn.courtside.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class Store {
  final JdbcTemplate jdbc;
  final ObjectMapper json;
  static final Set<String> TABLES =
      Set.of(
          "users",
          "tokens",
          "tournaments",
          "members",
          "entrants",
          "courts",
          "matches",
          "submissions",
          "audit");

  public Store(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
    for (String table : TABLES)
      jdbc.execute(
          "CREATE TABLE IF NOT EXISTS cs_"
              + table
              + " (id VARCHAR(100) PRIMARY KEY, scope VARCHAR(100) NOT NULL, data TEXT NOT NULL)");
  }

  private String table(String name) {
    if (!TABLES.contains(name)) throw new IllegalArgumentException("Unknown table");
    return "cs_" + name;
  }

  String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  <T> T decode(String value, Class<T> type) {
    try {
      return json.readValue(value, type);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  <T> T get(String name, String id, Class<T> type) {
    List<String> rows =
        jdbc.query(
            "SELECT data FROM " + table(name) + " WHERE id=?", (rs, n) -> rs.getString(1), id);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "数据不存在或已移除");
    return decode(rows.get(0), type);
  }

  boolean exists(String name, String id) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + table(name) + " WHERE id=?", Integer.class, id)
        > 0;
  }

  <T> List<T> list(String name, String scope, Class<T> type) {
    List<String> rows =
        scope == null
            ? jdbc.query(
                "SELECT data FROM " + table(name) + " ORDER BY id", (rs, n) -> rs.getString(1))
            : jdbc.query(
                "SELECT data FROM " + table(name) + " WHERE scope=? ORDER BY id",
                (rs, n) -> rs.getString(1),
                scope);
    return rows.stream().map(r -> decode(r, type)).toList();
  }

  void insert(String name, String id, String scope, Object value) {
    jdbc.update(
        "INSERT INTO " + table(name) + " (id,scope,data) VALUES (?,?,?)", id, scope, encode(value));
  }

  void save(String name, String id, String scope, Object value) {
    jdbc.update(
        "UPDATE " + table(name) + " SET scope=?,data=? WHERE id=?", scope, encode(value), id);
  }

  void lock(String tournamentId) {
    jdbc.queryForObject(
        "SELECT id FROM cs_tournaments WHERE id=? FOR UPDATE", String.class, tournamentId);
  }

  static String id() {
    return UUID.randomUUID().toString();
  }

  static String now() {
    return java.time.Instant.now().toString();
  }
}
