package net.bestia.model.battle

import java.io.Serializable

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Simple class for damage representation. The damage is tied to an entity.
 *
 * @author Thomas Felix
 */
data class Damage(
    val damage: Int,
    val type: DamageType
) : Serializable {

  init {
    require(damage >= 0) { "Damage can not be negative." }
  }

  companion object {
    /**
     * Constructs a damage object of type hit with the given amount of damage.
     *
     * @param uuid
     * Entity UUID to get hit by the damage.
     * @param hitAmount
     * Amount of damage taken.
     * @return The damage object.
     */
    fun getHit(hitAmount: Int): Damage {
      return Damage(hitAmount, DamageType.HIT)
    }

    /**
     * Constructs a damage object of type heal with the given amount of heal.
     *
     * @param uuid
     * Entity UUID to get hit by the damage.
     * @param hitAmount
     * Amount of damage taken.
     * @return The damage object.
     */
    fun getHeal(healAmount: Int): Damage {
      return Damage(healAmount, DamageType.HEAL)
    }

    /**
     * Constructs a damage object of type miss.
     *
     * @param uuid
     * Entity UUID to get hit by the damage miss.
     * @return The damage object.
     */
    val miss: Damage
      get() = Damage(0, DamageType.MISS)

    /**
     * Constructs a damage object of type critical with the given amount of
     * damage.
     *
     * @param uuid
     * Entity UUID to get hit by the damage.
     * @param hitAmount
     * Amount of damage taken.
     * @return The damage object.
     */
    fun getCrit(hitAmount: Int): Damage {
      return Damage(hitAmount, DamageType.CRITICAL)
    }
  }
}
