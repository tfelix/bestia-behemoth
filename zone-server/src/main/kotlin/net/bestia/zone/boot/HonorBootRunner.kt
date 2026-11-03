package net.bestia.zone.boot

import net.bestia.zone.honor.LocalHonorRegistry
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Loads what towns think of each Master. Beside [RumourBootRunner], after the world record exists. */
@Component
@Order(7)
class HonorBootRunner(
  private val registry: LocalHonorRegistry,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    registry.loadAll()
  }
}
