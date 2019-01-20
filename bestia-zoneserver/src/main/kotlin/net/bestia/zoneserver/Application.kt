package net.bestia.zoneserver

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["net.bestia.zoneserver"])
@EnableJpaRepositories(basePackages = ["net.bestia.model"])
@EntityScan(basePackages = ["net.bestia.model"])
class Application

fun main(args: Array<String>) {
  SpringApplication.run(Application::class.java, *args)
}
