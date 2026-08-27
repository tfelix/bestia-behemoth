package net.bestia.zone.ecs.battle.status

import net.bestia.zone.battle.status.RegenerationCalculator
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Passive HP regeneration for every entity with [Health], gated on the entity not being
 * [InCombat]. Amount and the 6 s cadence come from the docs' HP-recovery formula, via
 * [RegenerationCalculator.hpRegen]:
 * https://docs.bestia-game.net/docs/mechanics/statusvalues/
 */
@SpringComponent
@Order(56)
class HpRegenSystem(
  private val regenerationCalculator: RegenerationCalculator
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(6f)
  override val reads: ComponentClassSet = setOf(
    StatusValues::class,
    InCombat::class,
    Dead::class,
    RegenerationModifiers::class
  )
  override val writes: ComponentClassSet = setOf(Health::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Health::class).each { id ->
      // A player body lies dead until it respawns, and InCombat lapses after ten seconds - without
      // this the corpse would quietly heal itself back to full while still tagged Dead.
      if (world.has(id, Dead::class)) return@each
      if (world.has(id, InCombat::class)) return@each

      val health = get<Health>()
      if (health.current >= health.max) return@each

      // No attributes, no passive regeneration - an entity that was never given StatusValues has no
      // vitality to regenerate from. Explicit rather than defaulting VIT to 0, which used to come
      // out as a zero amount only by accident of rounding and would now be a floor of 1 per tick.
      val vitality = world.get(id, StatusValues::class)?.vitality ?: return@each

      // Fetched rather than queried on: an entity with nothing modifying its regeneration has no
      // RegenerationModifiers at all, and joining it into the query above would restrict
      // regeneration to buffed entities only.
      val modifier = world.get(id, RegenerationModifiers::class)?.hp
      val base = regenerationCalculator.hpRegen(health.max, vitality)

      health.current += regenerationCalculator.applyModifier(base, modifier)
    }
  }
}
