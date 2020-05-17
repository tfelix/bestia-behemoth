package net.bestia.zoneserver

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication(scanBasePackages = ["net.bestia.zoneserver"])
@EnableJpaRepositories(basePackages = ["net.bestia.model"])
@EntityScan(basePackages = ["net.bestia.model"])
@EnableNeo4jRepositories(basePackages = ["net.bestia.zoneserver"])
@EnableTransactionManagement
class Application

fun main(args: Array<String>) {
  SpringApplication.run(Application::class.java, *args)
}
