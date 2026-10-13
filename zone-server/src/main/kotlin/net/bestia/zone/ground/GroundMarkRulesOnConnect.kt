package net.bestia.zone.ground

import net.bestia.zone.message.SMSG
import net.bestia.zone.world.stream.ConnectMessageSource
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Component

/**
 * Gives every client the rules for the ground marks it lays down itself, before it is sent any column's marks.
 *
 * Built per connection rather than once, so the message always reflects the configs and the clock as they are.
 */
@Component
class GroundMarkRulesOnConnect(
  private val stamps: GroundStampConfig,
  private val wear: GroundWearConfig,
  private val blood: GroundBloodConfig,
  private val clock: BestiaClock,
) : ConnectMessageSource {

  override fun connectMessages(): List<SMSG> {
    return listOf(GroundMarkRulesSMSG.of(stamps, wear, blood, clock.speedFactor))
  }
}
