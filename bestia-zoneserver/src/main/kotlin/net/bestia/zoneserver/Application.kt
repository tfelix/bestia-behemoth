package net.bestia.zoneserver

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(
    scanBasePackages = ["net.bestia"]
)
@EnableJpaRepositories(basePackages = ["net.bestia.model.dao"])
@EntityScan(basePackages = ["net.bestia.model.domain"])
class Application

fun main(args: Array<String>) {
  SpringApplication.run(Application::class.java, *args)
}
