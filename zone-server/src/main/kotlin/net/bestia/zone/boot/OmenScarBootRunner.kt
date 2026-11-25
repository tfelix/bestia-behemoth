package net.bestia.zone.boot

import net.bestia.zone.prop.omen.OmenScars
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Loads the scars the mana left, so they stand after a restart. In the "things about the world" group. */
@Component
@Order(6)
class OmenScarBootRunner(
  private val scars: OmenScars,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    scars.loadAll()
  }
}
