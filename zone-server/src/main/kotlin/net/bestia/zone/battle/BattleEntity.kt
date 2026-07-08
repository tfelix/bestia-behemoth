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
  val assumedElement: Element
)