package net.bestia.zoneserver.entity

/**
 * I am still not sure if this makes sense to describe different kind of entity types
 * as the description of functionality and status values etc. should be controlled
 * by e.g. status values.
 */
enum class EntityType {
  /**
   * This tagged entity is a usual bestia mob.
   */
  MOB,

  /**
   * This tagged entity is a usual bestia NPC.
   */
  NPC,

  /**
   * Items lying on the ground which can be picked up by the player.
   */
  ITEM,

  /**
   * A natural resource which can be harvested if the needed skills are
   * learned by the player.
   */
  RESOURCE,

  /**
   * Entity is under the control of a player.
   */
  PLAYER,

  /**
   * Player placeable structures like buildings.
   */
  STRUCTURE
}