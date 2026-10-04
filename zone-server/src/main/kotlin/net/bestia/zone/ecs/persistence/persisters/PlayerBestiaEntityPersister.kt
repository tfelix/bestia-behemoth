package net.bestia.zone.ecs.persistence.persisters

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.bestia.OwnedBestia
import net.bestia.zone.bestia.PlayerBestiaRepository
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.persistence.EntityPersister
import net.bestia.zone.ecs.persistence.EntitySnapshot
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class PlayerBestiaSnapshot(
  override val entityId: EntityId,
  val playerBestiaId: PlayerBestiaId,
  val x: Long,
  val y: Long,
  val z: Long,
  val level: Int,
  val died: Boolean,
) : EntitySnapshot

/**
 * Writes owned bestia back to their `player_bestia` row. They come back when their master is next selected,
 * through `OwnedBestiaSpawnService`, so nothing is rehydrated at startup.
 */
@Component
class PlayerBestiaEntityPersister(
  private val playerBestiaRepository: PlayerBestiaRepository,
) : EntityPersister {

  override val kind = "player_bestia"
  override val loadsAtStartup = false

  override fun supports(world: World, id: EntityId): Boolean {
    return world.has(id, OwnedBestia::class)
  }

  override fun snapshot(world: World, id: EntityId): EntitySnapshot? {
    val owned = world.get(id, OwnedBestia::class) ?: return null
    val pos = world.get(id, Position::class) ?: return null

    return PlayerBestiaSnapshot(
      entityId = id,
      playerBestiaId = owned.playerBestiaId,
      x = pos.x, y = pos.y, z = pos.z,
      level = world.get(id, Level::class)?.level ?: 1,
      died = world.has(id, Dead::class),
    )
  }

  @Transactional
  override fun persist(snapshots: List<EntitySnapshot>) {
    val bySnapshot = snapshots.filterIsInstance<PlayerBestiaSnapshot>()
    val rows = playerBestiaRepository.findAllById(bySnapshot.map { it.playerBestiaId }).associateBy { it.id }

    for (snap in bySnapshot) {
      val row = rows[snap.playerBestiaId]
      if (row == null) {
        LOG.warn { "Player bestia ${snap.playerBestiaId} was not found, cannot persist it" }
        continue
      }
      // Like a master: a body is not left where it fell for the next session.
      row.position = if (snap.died) row.spawnPosition else Vec3L(snap.x, snap.y, snap.z)
      row.level = snap.level
    }
    playerBestiaRepository.saveAll(rows.values)
  }

  override fun loadAll(world: World) {
    return
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
