package net.bestia.model.domain

/**
 * Gives the target type for a attack.
 *
 * @author Thomas Felix
 */
enum class AttackTarget {
  /**
   * The attack is targeted to the ground. Usually the player uses the
   * indicator to place the attack on a certain ground.
   */
  GROUND,

  /**
   * The attack can be targeted against another entity but is not usable
   * against friendly entities. (The user can override this though to target it to friendly entities).
   */
  ENEMY_ENTITY,

  /**
   * The attack is directed against the user bestia itself.
   */
  SELF,

  /**
   * The attack is targeted against a friendly entity (the user can override
   * this manually to target it also against enemy entities).
   */
  FRIENDLY_ENTITY
}
