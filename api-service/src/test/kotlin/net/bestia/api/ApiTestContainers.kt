package net.bestia.api

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.nio.file.Path

/** A real MariaDB for the Flyway schema, and a real Valkey running the zone's own ACL file. */
@TestConfiguration(proxyBeanMethods = false)
class ApiTestContainers {

  @Bean
  @ServiceConnection
  fun mariaDbContainer(): MariaDBContainer<*> {
    return MariaDBContainer(DockerImageName.parse("mariadb:11"))
  }

  companion object {
    val valkey: GenericContainer<*> = GenericContainer("valkey/valkey:8.1")
      .withCopyFileToContainer(
        MountableFile.forHostPath(Path.of("../zone-server/valkey/users.acl").toAbsolutePath()),
        "/etc/valkey/users.acl",
      )
      .withCommand("valkey-server", "--aclfile", "/etc/valkey/users.acl")
      .withExposedPorts(6379)
      .also { it.start() }
  }
}
