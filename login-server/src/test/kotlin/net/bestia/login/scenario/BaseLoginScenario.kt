package net.bestia.login.scenario

import io.micrometer.core.instrument.MeterRegistry
import net.bestia.login.TestcontainersConfiguration
import net.bestia.login.zone.KickStreamConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * Pinned to `test` rather than `dev`. Scenarios assert what an ordinary deployment does, and the `dev`
 * profile is not that: it hands every registration a raised `account.sign-up-role`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, KickStreamConfiguration::class)
@ActiveProfiles("test")
@Transactional
abstract class BaseLoginScenario {

  @Autowired
  protected lateinit var meters: MeterRegistry

  /**
   * A counter's value now, summed over every series matching [tags] (`key, value, key, value, ...`).
   */
  protected fun count(name: String, vararg tags: String): Double {
    return meters.get(name).tags(*tags).counters().sumOf { it.count() }
  }

  /**
   * Starts watching a counter. The context is shared by every scenario, so a test asserts how far a counter has
   * moved since it was watched, not what it reads, which depends on which scenarios ran before it.
   */
  protected fun watch(name: String, vararg tags: String): Watch {
    return Watch(count(name, *tags)) { count(name, *tags) }
  }

  protected class Watch(private val start: Double, private val read: () -> Double) {
    fun moved(): Double {
      return read() - start
    }
  }
}
