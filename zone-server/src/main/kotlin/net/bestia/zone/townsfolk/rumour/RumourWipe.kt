package net.bestia.zone.townsfolk.rumour

import net.bestia.zone.world.WorldScopedData
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * News is attached to a settlement index, and those are dense and re-used. The version columns would refuse a
 * survivor, so this is the tidy half - but a town talking about a battle outside a village the new world never
 * placed is what it would look like without both.
 */
@Component
@Order(4)
class RumourWipe(
  private val rumourRepository: RumourRepository,
) : WorldScopedData {

  override fun wipe() {
    rumourRepository.deleteAll()
  }
}
