package net.bestia.loginserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = [
  "net.bestia.model",
  "net.bestia.loginserver"
])
@EntityScan("net.bestia.model")
@EnableJpaRepositories("net.bestia.model")
class LoginserverApplication

fun main(args: Array<String>) {
  runApplication<LoginserverApplication>(*args)
}
