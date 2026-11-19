package net.bestia.zone.text

import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Learns which texts are written before anything shows one. With the other things about the world. */
@Component
@Order(8)
class WorldTextBootRunner(private val texts: WorldTexts) : CommandLineRunner {

  override fun run(vararg args: String?) {
    texts.refreshWritten()
  }
}
