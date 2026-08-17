package net.bestia.zone.ecs.battle.status

import net.bestia.zone.battle.status.RegenerationCalculator
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Passive Mana regeneration for every entity with [Mana], gated on the entity not being
 * [InCombat]. Amount and the 8 s cadence come from the docs' mana-recovery formula, via
 * [RegenerationCalculator.manaRegen]:
 * https://docs.bestia-game.net/docs/mechanics/statusvalues/
 */
@SpringComponent
@Order(57)
class ManaRegenSystem(
  private val regenerationCalculator: RegenerationCalculator
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(8f)
  override val reads: ComponentClassSet = setOf(StatusValues::class, InCombat::class)
  override val writes: ComponentClassSet = setOf(Mana::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Mana::class).each { id ->
      if (world.has(id, InCombat::class)) return@each

      val mana = get<Mana>()
      if (mana.current >= mana.max) return@each

      // Same reasoning as HpRegenSystem: no attributes, no passive regeneration.
      val intelligence = world.get(id, StatusValues::class)?.intelligence ?: return@each

      mana.current += regenerationCalculator.manaRegen(mana.max, intelligence)
    }
  }
}
