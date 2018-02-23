package net.bestia.memoryserver

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ComponentScan

/**
 * Main application entry point. This will start the spring-boot application.
 *
 * @author Thomas Felix
 */
@SpringBootApplication
@ComponentScan("net.bestia.model.dao")
class Application

fun main(args: Array<String>) {
  SpringApplication.run(Application::class.java, *args)
}