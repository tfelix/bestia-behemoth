package net.bestia.zone.boot

import net.bestia.zone.worldevent.rift.Rifts
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Loads the rifts still forming or open. In the "things about the world" group. */
@Component
@Order(6)
class RiftBootRunner(
  private val rifts: Rifts,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    rifts.loadAll()
  }
}
