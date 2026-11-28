package net.bestia.zone.battle.ecs.status

import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId
import kotlin.math.max
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.RegenerationCalculator
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component as SpringComponent

/**
 * Passive Stamina regeneration. Amount and the 10 s cadence come from the docs' stamina-recovery formula, via
 * [RegenerationCalculator.staminaRegen]: https://docs.bestia-game.net/docs/mechanics/combat/statusvalues/
 *
 * ### Masters and owned bestias recover only by resting
 *
 * Food is what keeps a body going, so rest alone is kept small: stamina comes back only while `RESTING`, at
 * [StaminaConfig.restRegenPercent] of the formula, never past [StaminaConfig.regenCapPercent], not while
 * `WORN_OUT` from work and not while the weather bites. A worker who does not eat therefore cannot work, rest a
 * moment and work on. Wild mobs carry a placeholder stamina and keep the plain formula.
 *
 * ### Why this is not gated on `InCombat`, unlike its two siblings
 *
 * The docs' stamina section says nothing about combat. Gating regeneration on combat would mean a player being
 * poked while crossing a blizzard can never recover the stamina that is keeping them alive, which turns two
 * independent mechanics into a death spiral.
 */
@SpringComponent
class StaminaRegenSystem(
  private val regenerationCalculator: RegenerationCalculator,
  private val config: StaminaConfig = StaminaConfig(),
  rules: WorldRulesConfig = WorldRulesConfig(tickRate = DEFAULT_TICK_RATE),
) : System {
  override val phase = Phase.RECOVERY

  override val schedule: Schedule = Schedule.EverySeconds(REGEN_INTERVAL_SECONDS)
  override val reads: ComponentClassSet =
    setOf(CombatStats::class, Dead::class, RegenerationModifiers::class, StatusEffects::class, Master::class, OwnedBestia::class)
  override val writes: ComponentClassSet = setOf(Stamina::class)

  private val exposureMemoryTicks: Long = (config.exposureMemorySeconds * rules.tickRate).toLong()

  override fun update(world: World, deltaTime: Float) {
    world.query(Stamina::class).each { id ->
      // Nothing regenerates while dead - see HpRegenSystem.
      if (world.has(id, Dead::class)) return@each

      val stamina = get<Stamina>()
      val ceiling = if (feedsOnFood(world, id)) restCeilingOf(world, id, stamina) else stamina.max
      if (stamina.current >= ceiling) return@each

      // No attributes means no passive regeneration, and a missing RegenerationModifiers an unmodified rate.
      val attributes = world.get(id, CombatStats::class)?.values?.attributes ?: return@each

      val base = regenerationCalculator.staminaRegen(stamina.max, attributes.vitality, attributes.willpower)
      val rate = if (feedsOnFood(world, id)) max(1, base * config.restRegenPercent / 100) else base
      val modifier = world.get(id, RegenerationModifiers::class)?.stamina

      stamina.current = minOf(ceiling, stamina.current + regenerationCalculator.applyModifier(rate, modifier))
    }
  }

  private fun feedsOnFood(world: World, id: EntityId): Boolean {
    return world.has(id, Master::class) || world.has(id, OwnedBestia::class)
  }

  /** How far rest alone brings this body back now: nowhere while it may not rest, else up to the cap. */
  private fun restCeilingOf(world: World, id: EntityId, stamina: Stamina): Int {
    val effects = world.get(id, StatusEffects::class)
    val resting = effects?.hasEffect(StatusEffectId.RESTING.id) == true
    val wornOut = effects?.hasEffect(StatusEffectId.WORN_OUT.id) == true
    val exposed = stamina.isExposed(world.tickCount, exposureMemoryTicks)

    return if (resting && !wornOut && !exposed) stamina.max * config.regenCapPercent / 100 else 0
  }

  private companion object {
    const val REGEN_INTERVAL_SECONDS = 10f
    const val DEFAULT_TICK_RATE = 20
  }
}
