package net.bestia.api

import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * The running service the scenario tests share. They have to share one Spring context, not only look alike: a second
 * context in the same JVM would listen on the same bus reply channel and join the same consumer group as the first,
 * and take half of its replies and push events.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ApiTestContainers::class, RecordingPush::class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ApiScenarioTestBase {

  companion object {
    @JvmStatic
    @DynamicPropertySource
    fun bus(registry: DynamicPropertyRegistry) {
      registry.add("bus.host") { ApiTestContainers.valkey.host }
      registry.add("bus.port") { ApiTestContainers.valkey.getMappedPort(6379) }
    }
  }
}
