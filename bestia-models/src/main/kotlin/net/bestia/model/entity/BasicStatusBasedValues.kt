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
    override val criticalHitrate: Int = 0,
    override val dodge: Int = 0,
    override val casttimeMod: Float = 1f,
    override val spellDurationMod: Float = 1f,
    override val hitrate: Int = 1000,
    override val minDamage: Int = 60,
    override val rangedBonusDamage: Int = 0,
    override val attackSpeed: Float = 0f,
    override val walkspeed: Float = 1f,
    override val cooldownMod: Float = 1.0f,
    override val hpRegenRate: Float = 0f,
    override val manaRegenRate: Float = 0f
) : Serializable, StatusBasedValues {

  /**
   * Returns the health value ticked per regeneration step. Note that this
   * value might be smaller then 1. We use this to save the value between the
   * ticks until we have at least 1 health and can add this to the user
   * status.
   *
   * @param entity The entity.
   * @return The ticked health value.
   */
  val healthTick: Float
    get() = hpRegenRate / 1000 * REGENERATION_TICK_RATE_MS


  /**
   * Returns the mana value ticked per regeneration step. Note that this value
   * might be smaller then 1. We use this to save the value between the ticks
   * until we have at least 1 mana and can add this to the user status.
   *
   * @param entity The entity.
   * @return The ticked mana value.
   */
  val getManaTick: Float
    get() = manaRegenRate / 1000 * REGENERATION_TICK_RATE_MS

  companion object {
    fun create(status: StatusValues, level: Int = 1): BasicStatusBasedValues {
      if (level < 1) {
        throw IllegalArgumentException("Level can not be smaller then 1.")
      }

      val hpRegen = (status.vitality * 4 + level) / 100.0f
      val manaRegen = (status.vitality * 1.5f + level) / 100.0f

      return BasicStatusBasedValues(manaRegenRate = manaRegen, hpRegenRate = hpRegen)
    }

    /**
     * How often the regeneration should tick for each entity.
     */
    const val REGENERATION_TICK_RATE_MS = 8000L
  }
}
