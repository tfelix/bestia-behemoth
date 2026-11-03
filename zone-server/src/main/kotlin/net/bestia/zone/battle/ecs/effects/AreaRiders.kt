package net.bestia.zone.battle.ecs.effects

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.status.StatusEffectId

/**
 * What an area does besides its damage, applied by [AreaEffectSystem] on every tick to whoever stands inside.
 * Statuses and knockback hit the caster's foes; a zone status and a heal reach its own side when [forAllies].
 */
data class AreaRiders(
  val statuses: List<AreaStatus> = emptyList(),

  /** Refreshed on every tick, so it falls off soon after the bearer walks out. */
  val zoneStatus: StatusEffectId? = null,
  val forAllies: Boolean = false,
  val healPerTick: Int = 0,

  /** Max mana everyone inside gains per tick, in percent, foe and friend alike. */
  val manaPercentPerTick: Int = 0,

  /** Tiles a foe is pushed away from the centre on each tick. */
  val knockbackTiles: Int = 0,

  /** Only creatures of these kind names or base elements are touched; empty sets mean everyone. */
  val affectsKinds: Set<String> = emptySet(),
  val affectsElements: Set<Element> = emptySet(),

  /** Tiles per second the area drifts, like a hurricane moving off from its caster. */
  val driftX: Float = 0f,
  val driftY: Float = 0f,
) {

  val drifts: Boolean
    get() = driftX != 0f || driftY != 0f

  companion object {
    val NONE = AreaRiders()
  }
}
