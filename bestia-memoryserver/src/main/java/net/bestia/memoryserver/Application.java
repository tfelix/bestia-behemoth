package net.bestia.memoryserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main application entry point. This will start the spring-boot application.
 *
 * @author Thomas Felix
 */
@SpringBootApplication
@ComponentScan("net.bestia.model.dao")
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
