package net.bestia.login.zone

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.MountableFile
import java.nio.file.Path

/** A real Valkey running the zone's own ACL file, so the scenarios prove the `login` user may append its kicks. */
@TestConfiguration(proxyBeanMethods = false)
class KickStreamConfiguration {

  @Bean
  fun valkeyContainer(): GenericContainer<*> {
    return GenericContainer("valkey/valkey:8.1")
      .withCopyFileToContainer(
        MountableFile.forHostPath(Path.of("../zone-server/valkey/users.acl").toAbsolutePath()),
        "/etc/valkey/users.acl",
      )
      .withCommand("valkey-server", "--aclfile", "/etc/valkey/users.acl")
      .withExposedPorts(VALKEY_PORT)
  }

  @Bean
  fun busProperties(valkeyContainer: GenericContainer<*>): DynamicPropertyRegistrar {
    return DynamicPropertyRegistrar { registry ->
      registry.add("bus.host") { valkeyContainer.host }
      registry.add("bus.port") { valkeyContainer.getMappedPort(VALKEY_PORT) }
    }
  }

  @Bean(destroyMethod = "close")
  fun kickStream(valkeyContainer: GenericContainer<*>): KickStream {
    return KickStream(valkeyContainer.host, valkeyContainer.getMappedPort(VALKEY_PORT))
  }

  private companion object {
    const val VALKEY_PORT = 6379
  }
}
