package net.bestia.model.entity

import java.io.Serializable

import net.bestia.model.bestia.StatusPoints
import net.bestia.model.map.Walkspeed

/**
 * These modifier are calculates based on status values. The are used to
 * calculate various aspects of the game.
 *
 * @author Thomas Felix
 */
class StatusBasedValuesImpl(
    private val status: StatusPoints,
    private var level: Int,
    override var criticalHitrate: Int = 0,
    override var dodge: Int = 0,
    override var casttimeMod: Float = 1f,
    override var spellDurationMod: Float = 1f,
    override var hitrate: Int = 1000,
    override var minDamage: Int = 60,
    override var rangedBonusDamage: Int = 0,
    override var attackSpeed: Float = 0f,
    override var walkspeed: Walkspeed = Walkspeed(1f),
    override var cooldownMod: Float = 1.0f
) : Serializable, StatusBasedValues {

  init {
    if (level < 1) {
      throw IllegalArgumentException("Level can not be smaller then 1.")
    }
  }

  override var hpRegenRate = 0f
    get() = (status.vitality * 4 + level) / 100.0f

  override var manaRegenRate: Float = 0f
    get() = (status.vitality * 1.5f + level) / 100.0f
}
