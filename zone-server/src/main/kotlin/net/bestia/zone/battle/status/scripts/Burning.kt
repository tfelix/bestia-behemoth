package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ElementModifier
import net.bestia.zone.battle.GroundIgnition
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.navigation.region.RegionGrid
import org.springframework.stereotype.Component
import java.util.Random

/**
 * On fire: fire damage every second, weighed against the bearer's element so a fire creature shrugs it off, and
 * now and then the grass underfoot catches. Ice and water put it out.
 */
@Component
class Burning(
  private val water: RegionGrid,
  private val ignition: GroundIgnition = GroundIgnition.NONE,
  private val random: Random = ThreadLocalRandomSource,
) : StatusEffectScript {

  override val tickIntervalSeconds: Float = 1f

  override fun durationSeconds(level: Int): Double {
    return 8.0
  }

  override fun onApply(context: StatusEffectHookContext) {
    context.removeEffect(StatusEffectId.FROZEN)
  }

  override fun onTick(context: StatusEffectTickContext) {
    val world = context.world
    val at = world.get(context.hostId, Position::class)?.toVec3L() ?: return
    if (water.isWaterAt(at)) {
      context.removeSelf()
      return
    }

    val health = world.get(context.hostId, Health::class) ?: return
    val element = world.get(context.hostId, CombatStats::class)?.values?.elementOverride
      ?: world.get(context.hostId, Nature::class)?.element
      ?: Element.NORMAL
    val burn = health.max * BURN_PERCENT / 100f * ElementModifier.getModifierFloat(Element.FIRE, element)
    context.dealDamage(burn.toInt().coerceAtLeast(if (burn > 0f) 1 else 0))

    if (random.nextInt(100) < IGNITE_CHANCE_PERCENT) {
      ignition.ignite(at, context.effect.sourceEntityId ?: context.hostId)
    }
  }

  private companion object {
    const val BURN_PERCENT = 2
    const val IGNITE_CHANCE_PERCENT = 15
  }
}
