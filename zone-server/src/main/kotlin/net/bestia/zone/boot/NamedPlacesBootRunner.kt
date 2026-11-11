package net.bestia.zone.boot

import net.bestia.zone.master.bestia.NamedPlaces
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Loads the masters' named places before the tick starts, so a sense never reaches the database for one. */
@Component
@Order(8)
class NamedPlacesBootRunner(
  private val places: NamedPlaces,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    places.loadAll()
  }
}
