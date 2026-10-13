package com.smartline.smartos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// UserDetailsServiceAutoConfiguration excluded: authentication is stateless
// JWT only, so its generated in-memory user would be dead config + scary logs.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SmartOsApplication {

  public static void main(String[] args) {
    SpringApplication.run(SmartOsApplication.class, args);
  }
}
