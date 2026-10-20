package cn.courtside.server;

import cn.courtside.shared.Models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;

@Service
class Auth {
  public static class Account {
    public User user = new User();
    public String hash = "";
  }

  public static class Token {
    public String userId = "";
    public long expiresAt;
  }

  final Store db;
  final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
  final Map<String, long[]> attempts = new ConcurrentHashMap<>();

  Auth(Store db) {
    this.db = db;
  }

  static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  synchronized void create(String username, String name, String role, String password) {
    ApiError.check(
        username != null
            && username.matches("[a-zA-Z0-9_.-]{3,40}")
            && name != null
            && !name.isBlank(),
        400,
        "VALIDATION",
        "账号需为3至40位字母、数字或下划线，姓名不能为空");
    ApiError.check(
        password != null && password.length() >= 10 && password.length() <= 72,
        400,
        "VALIDATION",
        "密码长度需为10至72位");
    ApiError.check(
        db.list("users", username, Account.class).isEmpty(), 409, "USERNAME_EXISTS", "该账号已存在");
    Account a = new Account();
    a.user.id = Store.id();
    a.user.username = username;
    a.user.name = name;
    a.user.role = role;
    a.hash = passwords.encode(password);
    db.insert("users", a.user.id, username, a);
  }

  synchronized Login login(String username, String password, String remote) {
    long now = System.currentTimeMillis();
    long[] rate = attempts.computeIfAbsent(remote, k -> new long[] {now, 0});
    if (now - rate[0] > 60000) {
      rate[0] = now;
      rate[1] = 0;
    }
    ApiError.check(rate[1] < 10, 429, "RATE_LIMIT", "尝试次数较多，请一分钟后再试");
    rate[1]++;
    List<Account> accounts = db.list("users", username, Account.class);
    ApiError.check(
        !accounts.isEmpty() && passwords.matches(password, accounts.get(0).hash),
        401,
        "BAD_CREDENTIALS",
        "账号或密码不正确");
    attempts.remove(remote);
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    Login login = new Login();
    login.token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    login.user = accounts.get(0).user;
    Token token = new Token();
    token.userId = login.user.id;
    token.expiresAt = now + 7L * 24 * 3600 * 1000;
    db.insert("tokens", digest(login.token), login.user.id, token);
    return login;
  }

  User authenticate(String value) {
    String key = digest(value);
    ApiError.check(db.exists("tokens", key), 401, "UNAUTHENTICATED", "登录已失效，请重新登录");
    Token t = db.get("tokens", key, Token.class);
    ApiError.check(t.expiresAt > System.currentTimeMillis(), 401, "TOKEN_EXPIRED", "登录已过期，请重新登录");
    return db.get("users", t.userId, Account.class).user;
  }
}

@Configuration
class Security {
  @Bean
  SecurityFilterChain chain(HttpSecurity http, Auth auth, ObjectMapper json) throws Exception {
    OncePerRequestFilter bearer =
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              HttpServletRequest req, HttpServletResponse res, FilterChain chain)
              throws ServletException, IOException {
            String header = req.getHeader("Authorization");
            try {
              if (header != null && header.startsWith("Bearer ")) {
                User user = auth.authenticate(header.substring(7));
                req.setAttribute("user", user);
                SecurityContextHolder.getContext()
                    .setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, List.of()));
              }
              chain.doFilter(req, res);
            } catch (ApiError e) {
              res.setStatus(e.status);
              res.setContentType("application/json;charset=UTF-8");
              json.writeValue(res.getWriter(), Map.of("code", e.code, "message", e.getMessage()));
            }
          }
        };
    return http.csrf(c -> c.disable())
        .formLogin(c -> c.disable())
        .httpBasic(c -> c.disable())
        .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            c ->
                c.requestMatchers("/api/auth/login", "/api/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            c ->
                c.authenticationEntryPoint(
                    (req, res, e) -> {
                      res.setStatus(401);
                      res.setContentType("application/json;charset=UTF-8");
                      json.writeValue(
                          res.getWriter(), Map.of("code", "UNAUTHENTICATED", "message", "请先登录"));
                    }))
        .addFilterBefore(bearer, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
