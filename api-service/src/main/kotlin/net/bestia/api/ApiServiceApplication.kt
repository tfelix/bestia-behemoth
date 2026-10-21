package net.bestia.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/** Scheduling drives the WebSocket keep-alive and the sweep of expired socket sessions. */
@SpringBootApplication
@ConfigurationPropertiesScan("net.bestia.api")
@EnableScheduling
class ApiServiceApplication

fun main(args: Array<String>) {
  runApplication<ApiServiceApplication>(*args)
}
