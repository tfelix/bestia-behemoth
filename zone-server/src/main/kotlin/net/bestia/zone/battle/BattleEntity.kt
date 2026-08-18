package net.bestia.zone.battle

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.battle.status.DefenseValues
import net.bestia.zone.battle.status.DerivedStatusValues
import net.bestia.zone.battle.status.StatusValues

data class BattleEntity(
  val id: Long,
  val position: Vec3L,
  val level: Int,
  val defense: DefenseValues,
  val statusValues: StatusValues,
  val derivedStatusValues: DerivedStatusValues,
  /**
   * Current assumed element either natural, via armor or buff.
   */
  val assumedElement: Element,

  /** 0 when the entity has no health at all, which is why a heal has to guard against it. */
  val maxHealth: Int = 0,

  /**
   * Ids of the status effects currently on this entity, so a script can refuse a cast the target is
   * not eligible for - First Aid's once-a-minute limit is exactly this and nothing else.
   */
  val activeEffectIds: Set<Long> = emptySet()
)