package net.bestia.login

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.utility.DockerImageName

/**
 * A real MariaDB for the tests rather than an in-memory substitute.
 *
 * The schema is owned by Flyway and checked by `ddl-auto: validate`, and neither of those means
 * anything against a different engine: H2 would happily accept DDL MariaDB rejects, and column type
 * mismatches - the exact thing validation exists to catch - would only surface in production.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  fun mariaDbContainer(): MariaDBContainer<*> {
    return MariaDBContainer(DockerImageName.parse("mariadb:11"))
  }
}
