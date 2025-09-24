package net.bestia.login

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan("net.bestia.login")
class LoginServerApplication

fun main(args: Array<String>) {
  runApplication<LoginServerApplication>(*args)
}