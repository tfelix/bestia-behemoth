package net.bestia.zone.battle.ecs.effects

import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.status.StatusEffectId

/**
 * What an area does besides its damage, applied by [AreaEffectSystem] on every tick to whoever stands inside.
 * Statuses and knockback hit the caster's foes; a zone status and a heal reach its own side when [forAllies].
 */
data class AreaMechanics(
  val statuses: List<AreaStatus> = emptyList(),

  /** Refreshed on every tick, so it falls off soon after the bearer walks out. */
  val zoneStatus: StatusEffectId? = null,
  val forAllies: Boolean = false,
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

  /** Only foes of these kinds are harmed; empty means every foe. Allies are healed whatever their kind. */
  val foeKinds: Set<BestiaKind> = emptySet(),

  /** Percent of an ally's maximum health restored per tick, beside the flat [healPerTick]. */
  val healPercentPerTick: Int = 0,

  /** Weighs each tick's damage by the area's element against the victim's, as a bolt is weighed. */
  val weighsElement: Boolean = false,

  /** No ground spell takes root inside it unless its Potency wins, see `GroundWards`. */
  val blocksGroundSpells: Boolean = false,

  /** Moves with its caster instead of staying where it was cast. */
  val followsCaster: Boolean = false,

  /** Pushes a foe back the way it came, against its facing, instead of away from the centre. */
  val knockbackAgainstFacing: Boolean = false,

  /** Its ally side is the caster's party and their bestias only, as a camp is; other players get nothing. */
  val partyOnly: Boolean = false,
) {

  val drifts: Boolean
    get() = driftX != 0f || driftY != 0f

  companion object {
    val NONE = AreaMechanics()
  }
}
