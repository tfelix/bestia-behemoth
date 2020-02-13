package net.bestia.messages.entity

/**
 * Interaction types give the client hints how the player is able to interact
 * with an entity. Based upon the available options it should present the player
 * with appropriate UI options.
 *
 * @author Thomas Felix
 */
enum class Interaction {
  /**
   * The player can possibly attack this entity.
   */
  ATTACK,

  /**
   * NPCs who is usually friendly.
   */
  TALK,

  /**
   * Items get a short "drop" animation when they appear and the player is
   * able to click on them to interact via them.
   */
  PICKUP,

  /**
   * The player can interact with the entity via clicking on it. The entity
   * should handle such clicks via a special script component like displaying
   * a special GUI to the user.
   */
  INTERACT
}
