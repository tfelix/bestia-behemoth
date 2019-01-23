package net.bestia.zoneserver.entity.component

/**
 * The tag component allows attach simple data to an entity.
 *
 * @author Thomas Felix
 */
data class TagComponent(
    override val entityId: Long,
    val tags: Set<String>
) : Component {

  fun has(tag: String): Boolean {
    return tags.contains(tag)
  }

  companion object {
    /**
     * This tagged entity is a usual bestia mob.
     */
    const val MOB = "mob"

    /**
     * This tagged entity is a usual bestia NPC.
     */
    const val NPC = "npc"

    /**
     * Items lying on the ground which can be picked up by the player.
     */
    const val ITEM = "item"

    /**
     * A natural resource which can be harvested if the needed skills are
     * learned by the player.
     */
    const val RESOURCE = "resource"

    /**
     * Entity is under the control of a player.
     */
    const val PLAYER = "player"
  }
}
