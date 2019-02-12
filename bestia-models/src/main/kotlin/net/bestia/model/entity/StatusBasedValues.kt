package net.bestia.model.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

import net.bestia.model.bestia.StatusValues
import net.bestia.model.map.Walkspeed

/**
 * How often the regeneration should tick for each entity.
 */
const val REGENERATION_TICK_RATE_MS = 8000L

/**
 * The [StatusBasedValues] are used for advanced calculations. They are
 * usually based upon [StatusValues] and provide data needed for further
 * algorithms into the game. This implementation can be wrapped/decorated in
 * order to change the values based on modifier.
 *
 * @author Thomas Felix
 */
interface StatusBasedValues {

  /**
   * @return Returns the current HP regeneration per second.
   */
  @get:JsonProperty("hpr")
  val hpRegenRate: Float

  /**
   * @return The current mana regeneration per second.
   */
  @get:JsonProperty("manar")
  val manaRegenRate: Float

  /**
   * @return The current mana regeneration per second.
   */
  @get:JsonProperty("star")
  val staminaRegenRate: Float

  /**
   * Denotes the chance of a critical hit. Hit is a fixed point with 1/10
   * increments from 0 to 1000. (1000 means 100% crit chance).
   *
   * @return Critical hit percentage between 0 and 100% (in 1/10 increments,
   * thus 0 to 1000).
   */
  @get:JsonProperty("cr")
  val criticalHitrate: Int

  /**
   * Chance of dodging an enemy attack. This only applies for physical attacks
   * since magic can not be dodged. Fixed point value between 0 and 1000 (1/10
   * increments).
   *
   * @return Dodge percentage between 0 and 100% (in 1/10 increments, thus 0
   * to 1000).
   */
  @get:JsonProperty("d")
  val dodge: Int

  @get:JsonProperty("ct")
  val casttimeMod: Float

  @get:JsonProperty("cd")
  val cooldownMod: Float

  /**
   * Modifier to denote how LONG the spells incantation will last once they
   * have been casted. This does not apply to all spells. One shot damage
   * spells most likly wont benefit from this modifier but enchantments which
   * will persist for a certain amount of time into the world will certainly
   * do.
   *
   * @return A modifier of the cast duration.
   */
  @get:JsonProperty("cdu")
  val spellDurationMod: Float

  @get:JsonProperty("hr")
  val hitrate: Int

  @get:JsonIgnore
  val minDamage: Int

  @get:JsonIgnore
  val rangedBonusDamage: Int

  /**
   * This returns the attacks per second. This is only used for basic attacks
   * since skill attacks usually have their own cooldown timer. It basically says
   * attacks / s. The slower the value gets the faster the player hits.
   *
   * @return
   */
  @get:JsonProperty("aspd")
  val attackSpeed: Float

  @get:JsonProperty("w")
  val walkspeed: Float

  @get:JsonIgnore
  val healthTick: Float

  @get:JsonIgnore
  val manaTick: Float

  @get:JsonIgnore
  val staminaTick: Float
}