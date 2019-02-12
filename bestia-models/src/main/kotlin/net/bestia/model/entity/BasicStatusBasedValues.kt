package net.bestia.model.entity

import net.bestia.model.bestia.ConditionValues
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
    private val conditionValues: ConditionValues,
    private val level: Int,

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

  private val staminaPerc: Float
    get() = conditionValues.currentStamina.toFloat() / conditionValues.maxStamina

  override val walkspeed: Float
    get() {
      val modWalkspeed = if (staminaPerc < 0.3) 5f / 3 * staminaPerc else 1f

      return baseWalkspeed * modWalkspeed
    }

  /**
   * Returns the health value ticked per regeneration step. Note that this
   * value might be smaller then 1. We use this to save the value between the
   * ticks until we have at least 1 health and can add this to the user
   * status.
   *
   * @param entity The entity.
   * @return The ticked health value.
   */
  override val healthTick: Float
    get() {
      return if (staminaPerc < 0.1f) {
        0f
      } else if (staminaPerc < 0.5f) {
        conditionValues.maxHealth * -0.03f
      } else {
        hpRegenRate / 1000 * REGENERATION_TICK_RATE_MS
      }
    }

  /**
   * Returns the mana value ticked per regeneration step. Note that this value
   * might be smaller then 1. We use this to save the value between the ticks
   * until we have at least 1 mana and can add this to the user status.
   *
   * @param entity The entity.
   * @return The ticked mana value.
   */
  override val manaTick: Float
    get() {
      return if (staminaPerc < 0.1f) {
        0f
      } else {
        manaRegenRate / 1000 * REGENERATION_TICK_RATE_MS
      }
    }

  override val staminaTick: Float
    get() = staminaRegenRate / 1000 * REGENERATION_TICK_RATE_MS
}
