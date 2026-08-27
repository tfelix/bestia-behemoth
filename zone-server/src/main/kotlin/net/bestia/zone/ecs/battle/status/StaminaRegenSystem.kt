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
 * Passive Stamina regeneration for every entity with [Stamina]. Amount and the 10 s cadence come
 * from the docs' stamina-recovery formula, via [RegenerationCalculator.staminaRegen]:
 * https://docs.bestia-game.net/docs/mechanics/statusvalues/
 *
 * ### Why this is not gated on [InCombat], unlike its two siblings
 *
 * The docs' stamina section mentions only the resting doubling and says nothing about combat, and
 * stamina is not a combat resource here - its only consumer is
 * [net.bestia.zone.environment.weather.EnvironmentalExposureSystem], which drains it for being out
 * in the cold and then starts on health once it is gone. Gating regeneration on combat would mean a
 * player being poked while crossing a blizzard can never recover the stamina that is keeping them
 * alive, which turns two independent mechanics into a death spiral. Should stamina ever gain a
 * combat cost (a sprint, a heavy attack), revisit this - the gate is one line, copied from
 * [HpRegenSystem].
 */
@SpringComponent
@Order(58)
class StaminaRegenSystem(
  private val regenerationCalculator: RegenerationCalculator
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(10f)
  override val reads: ComponentClassSet =
    setOf(StatusValues::class, Dead::class, RegenerationModifiers::class)
  override val writes: ComponentClassSet = setOf(Stamina::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Stamina::class).each { id ->
      // Nothing regenerates while dead - see HpRegenSystem. This gate is about death, not combat,
      // so it applies here even though the InCombat one deliberately does not.
      if (world.has(id, Dead::class)) return@each

      val stamina = get<Stamina>()
      if (stamina.current >= stamina.max) return@each

      // Same reasoning as HpRegenSystem, for both of these: no attributes means no passive
      // regeneration, and a missing RegenerationModifiers means an unmodified rate.
      val statusValues = world.get(id, StatusValues::class) ?: return@each

      val modifier = world.get(id, RegenerationModifiers::class)?.stamina
      val base = regenerationCalculator.staminaRegen(
        stamina.max,
        statusValues.vitality,
        statusValues.willpower
      )

      stamina.current += regenerationCalculator.applyModifier(base, modifier)
    }
  }
}
