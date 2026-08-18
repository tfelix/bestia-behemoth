package net.bestia.zone.ecs.battle.status

import net.bestia.zone.battle.status.RegenModifier
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.Component

/**
 * Everything currently modifying this entity's HP / Mana / Stamina regeneration rates, resolved into
 * one [RegenModifier] per pool - worn equipment, active status effects and learned passive skills
 * all collapsed into a flat and a percentage term.
 *
 * Written **only** by `net.bestia.zone.ecs.battle.effects.StatusValueRecalcSystem`, via [copyFrom];
 * read by [HpRegenSystem], [ManaRegenSystem] and [StaminaRegenSystem], which resolve it against a
 * base rate through `RegenerationCalculator.applyModifier`.
 *
 * ### Absent means unmodified
 *
 * A mob, a promoted prop, or anything else that never goes through the recalc simply has no such
 * component and regenerates at its base rate. That is why the regen systems read it with
 * `world.get(...)` and tolerate null rather than joining it into their query - querying on it would
 * silently restrict regeneration to entities that happen to be buffed.
 *
 * ### Why it overwrites rather than accumulates
 *
 * [copyFrom] replaces all three values outright, including with neutral ones. The recalc rebuilds
 * its [StatusValueRecalcContext] from scratch on every pass, so a buff that expired contributes
 * nothing to the new context and must therefore stop contributing here - accumulating with `+=`
 * would make every expired effect permanent. The counterpart risk is the component going stale
 * because nothing triggers a recalc; that is what the `IsStatusValueDirty` markers on equipping,
 * effect expiry and skill learning are for. `net.bestia.zone.ecs.item.CarryCapacity` is the
 * cautionary tale - it caches its inputs for a `CarryCapacitySystem` that was never written, so a
 * weight limit still ignores buffs and level-ups entirely.
 *
 * Server-side bookkeeping only - deliberately not [net.bestia.zone.ecs.core.Dirtyable]. The client
 * sees the consequence in the [Health] / [Mana] / [Stamina] values it already receives and has no
 * use for knowing how the rate was arrived at, the same argument [FormulaDrivenVitals] makes.
 */
class RegenerationModifiers(
  var hp: RegenModifier = RegenModifier(),
  var mana: RegenModifier = RegenModifier(),
  var stamina: RegenModifier = RegenModifier()
) : Component {

  /**
   * Takes over the three accumulated modifiers from a finished recalc pass. Safe to share the
   * instances rather than copy their fields: [RegenModifier] is immutable, so nothing the next
   * recalc does to its own context can reach back into this component.
   */
  fun copyFrom(context: StatusValueRecalcContext) {
    hp = context.hpRegen
    mana = context.manaRegen
    stamina = context.staminaRegen
  }
}
