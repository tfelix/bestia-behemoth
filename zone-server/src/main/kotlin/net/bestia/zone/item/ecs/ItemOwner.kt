package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.container.ItemHolder
import net.bestia.zone.util.EntityId
import net.bestia.zone.util.PlayerBestiaId

/**
 * Whose container holds an entity's items: the master's own when [playerBestiaId] is null, else that bestia's.
 * Database writes for either are keyed by [masterId], so they stay ordered against each other.
 */
data class ItemOwner(
  val masterId: Long,
  val playerBestiaId: PlayerBestiaId?,
) {
  companion object {
    /** Read off the entity on the tick, which still knows it after a logout. Null for an entity no master owns. */
    fun of(world: World, entityId: EntityId): ItemOwner? {
      world.get(entityId, Master::class)?.let { return ItemOwner(it.masterId, null) }
      world.get(entityId, OwnedBestia::class)?.let { return ItemOwner(it.masterId, it.playerBestiaId) }

      return null
    }

    /**
     * Which holder [entityId] is for [masterId], or null when the master may not reach into it. Unlike [of] it knows
     * slot storages such as chests: one only takes and gives goods, and every caller of [of] reads a null bestia as
     * the master's own bag.
     */
    fun holderOf(world: World, entityId: EntityId, masterId: Long): ItemHolder? {
      world.get(entityId, SlotStorage::class)?.let { storage ->
        return if (storage.useOf(masterId) != null) storage.holder else null
      }
      val owner = of(world, entityId)?.takeIf { it.masterId == masterId } ?: return null

      return owner.playerBestiaId?.let(ItemHolder::Bestia) ?: ItemHolder.Master
    }
  }
}
