package net.bestia.zone.ecs.account

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.session.ConnectionInfoService.PlayerEntity
import net.bestia.zone.util.PlayerBestiaId

/**
 * Which master a player bestia belongs to. The source of truth for ownership: a bestia stays in the world
 * when its owner disconnects, and the session that knew about it does not.
 *
 * Not `Dirtyable`: ownership is server bookkeeping, the client learns it from `Account`.
 */
data class OwnedBestia(
  val masterId: Long,
  val playerBestiaId: PlayerBestiaId,
) : Component {

  companion object {
    /** The bestias in [world] that belong to [masterId]. A scan, but only player bestias carry this. */
    fun ownedBy(world: World, masterId: Long): Set<PlayerEntity> {
      val owned = HashSet<PlayerEntity>()
      world.query(OwnedBestia::class).each { id ->
        val ownership = get<OwnedBestia>()
        if (ownership.masterId == masterId) owned.add(PlayerEntity(ownership.playerBestiaId, id))
      }
      return owned
    }
  }
}
