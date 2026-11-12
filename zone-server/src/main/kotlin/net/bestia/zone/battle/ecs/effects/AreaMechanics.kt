package net.bestia.zone.battle.ecs.effects

import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.geometry.Vec3L

/**
 * What an area does besides its damage, applied by [AreaEffectSystem] on every tick to whoever stands inside.
 * Statuses and knockback hit the caster's foes; a zone status and a heal reach its own side when [forAllies].
 */
data class AreaMechanics(
  val statuses: List<AreaStatus> = emptyList(),

  /** Refreshed on every tick, so it falls off soon after the bearer walks out. */
  val zoneStatus: StatusEffectId? = null,
  val forAllies: Boolean = false,

  /** The [zoneStatus] reaches everyone inside, foe and friend alike, like Pneuma's still air. */
  val zoneStatusForEveryone: Boolean = false,
  val healPerTick: Int = 0,

  /** Max mana everyone inside gains per tick, in percent, foe and friend alike. */
  val manaPercentPerTick: Int = 0,

  /** Tiles a foe is pushed away from the centre on each tick. */
  val knockbackTiles: Int = 0,

  /** Only creatures of these kinds or base elements are touched; empty sets mean everyone. */
  val affectsKinds: Set<BestiaKind> = emptySet(),
  val affectsElements: Set<Element> = emptySet(),

  /** Tiles per second the area drifts, like a hurricane moving off from its caster. */
  val driftX: Float = 0f,
  val driftY: Float = 0f,

  /**
   * Above 0, each tick lands as one strike at a random spot inside the area, hitting only what stands within
   * this many tiles of it - a meteor or a lightning bolt rather than a carpet.
   */
  val strikeRadiusTiles: Int = 0,

  /**
   * Only foes of these kinds or base elements are harmed; empty sets mean every foe. Allies are healed whatever
   * their kind.
   */
  val foeKinds: Set<BestiaKind> = emptySet(),
  val foeElements: Set<Element> = emptySet(),

  /** Extra damage, in percent, on a Demon or an Undead foe, see `Unholy`. */
  val unholyBonusPercent: Int = 0,

  /** Percent of an ally's maximum health restored per tick, beside the flat [healPerTick]. */
  val healPercentPerTick: Int = 0,

  /** Weighs each tick's damage by the area's element against the victim's, as a bolt is weighed. */
  val weighsElement: Boolean = false,

  /**
   * Each tick lands as an `ElementalSpell` of the area's element: weighed by element and the victim's resistance to
   * it, less its MDEF, like a Wizard's bolt.
   */
  val landsAsElementalSpell: Boolean = false,

  /** No ground spell takes root inside it unless its Potency wins, see `GroundWards`. */
  val blocksGroundSpells: Boolean = false,

  /** Moves with its caster instead of staying where it was cast. */
  val followsCaster: Boolean = false,

  /**
   * For a wall: the direction it faces, away from its caster. A foe is pushed across the wall back to the side it
   * came from instead of away from the centre, so it can never be pushed along the wall and stay in the fire.
   */
  val knockbackAcross: Vec3L? = null,

  /** Its ally side is the caster's party and their bestias only, as a camp is; other players get nothing. */
  val partyOnly: Boolean = false,

  /**
   * Physical blows on the allies inside are turned away until this many are spent, all of them sharing one count,
   * like Safety Wall. The [zoneStatus] then names the area as its source, so the bearer knows whose count to spend.
   */
  val absorbsHits: Int = 0,
) {

  val drifts: Boolean
    get() = driftX != 0f || driftY != 0f

  companion object {
    val NONE = AreaMechanics()
  }
}
