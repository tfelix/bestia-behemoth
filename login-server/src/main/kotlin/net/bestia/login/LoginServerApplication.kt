package net.bestia.login

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Scheduling drives the sweepers for expired ceremonies, login sessions and authorization codes.
 * Those rows are abandoned far more often than they are completed - a player opens the browser and
 * closes the tab - so the destructive reads alone do not keep the tables bounded.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("net.bestia.login")
@EnableScheduling
class LoginServerApplication

fun main(args: Array<String>) {
  runApplication<LoginServerApplication>(*args)
}
