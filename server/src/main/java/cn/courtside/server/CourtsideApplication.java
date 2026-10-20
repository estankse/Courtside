package cn.courtside.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    exclude =
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
            .class)
public class CourtsideApplication {
  public static void main(String[] args) {
    SpringApplication.run(CourtsideApplication.class, args);
  }
}
