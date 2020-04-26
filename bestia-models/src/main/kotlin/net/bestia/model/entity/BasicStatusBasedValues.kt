package net.bestia.model.entity

import java.io.Serializable
import net.bestia.model.bestia.StatusValues

/**
 * These modifier are calculates based on status values. The are used to
 * calculate various aspects of the game.
 *
 * @author Thomas Felix
 */
data class BasicStatusBasedValues(
    private val statusValues: StatusValues,
    private val level: Int,

    // These values will soon be automatically calulcated from the provided values.
    override val criticalHitrate: Int = 0,
    override val dodge: Int = 0,
    override val casttimeMod: Float = 1f,
    override val spellDurationMod: Float = 1f,
    override val hitrate: Int = 1000,
    override val minDamage: Int = 60,
    override val rangedBonusDamage: Int = 0,
    override val attackSpeed: Float = 0f,
    private val baseWalkspeed: Float = 1f,
    override val cooldownMod: Float = 1.0f
) : Serializable, StatusBasedValues {

  override val hpRegenRate = (statusValues.vitality * 4 + statusValues.strength * 1.5f + level) / 100.0f
  override val manaRegenRate = (statusValues.willpower * 4 + level) / 100.0f
  override val staminaRegenRate = (statusValues.vitality * 4 + statusValues.willpower * 1.5f + level) / 100.0f

  override val walkspeed: Float
    get() {
      // We must calculate this in a service.
      // val modWalkspeed = if (staminaPerc < 0.3) 5f / 3 * staminaPerc else 1f

      return baseWalkspeed
    }
}
