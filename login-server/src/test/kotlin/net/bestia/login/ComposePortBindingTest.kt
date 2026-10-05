package net.bestia.login

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * The development database runs with known credentials. Docker publishes a bare port on every interface and
 * bypasses host firewalls such as ufw, so anyone on the network could log into it.
 */
class ComposePortBindingTest {

  @Test
  fun `the development database is only reachable from this machine`() {
    val compose = Yaml().load<Map<String, Any>>(File("compose.yaml").readText())

    @Suppress("UNCHECKED_CAST")
    val ports = (compose["services"] as Map<String, Map<String, Any>>).values
      .flatMap { service -> (service["ports"] as List<String>?).orEmpty() }

    assertTrue(ports.isNotEmpty())
    assertTrue(ports.all { it.startsWith("127.0.0.1:") }, "published on every interface: $ports")
  }
}
