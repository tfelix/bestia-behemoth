package net.bestia.zone.world.mana

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Component

/**
 * Lets the Wounds pour and the mana field creep, leak and drain once a minute, then settles the tiers. It walks only the tiles that hold
 * a change, so a world without Wounds that nobody touched costs nothing. Touches no component.
 */
@Component
class ManaFieldSystem(
  private val field: ManaField,
  private val ground: ManaGround,
  private val wounds: WoundSources,
  private val tiers: ManaTiers,
  config: ManaFieldConfig,
  clock: BestiaClock,
) : System {

  private val rule = ManaFieldRule(config, realSecondsPerBestiaDay = SECONDS_PER_DAY / clock.speedFactor)

  private val tierRule = ManaTierRule(dwellSeconds = config.tierDwellBestiaHours * SECONDS_PER_HOUR / clock.speedFactor)

  override val phase = Phase.UPKEEP

  override val schedule: Schedule = Schedule.EverySeconds(60f)

  override val reads: ComponentClassSet = emptySet()

  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    wounds.pour(deltaTime.toDouble())
    rule.step(field, ground, deltaTime.toDouble())
    tiers.update(tierRule, deltaTime.toDouble())
  }

  private companion object {
    const val SECONDS_PER_DAY = 86_400.0
    const val SECONDS_PER_HOUR = 3_600.0
  }
}
