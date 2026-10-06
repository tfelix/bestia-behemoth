package net.bestia.zone.ecs.persistence.persisters

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.bestia.PlayerBestiaRepository
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.EntityPersister
import net.bestia.zone.persistence.EntitySnapshot
import net.bestia.zone.account.SavePointService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Writes where a player bestia stands and its level back to its `player_bestia` row. The live entity does not
 * survive a restart; the row does.
 */
@Component
class PlayerBestiaEntityPersister(
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val savePointService: SavePointService,
) : EntityPersister {

  data class Snapshot(
    override val entityId: EntityId,
    val masterId: Long,
    val playerBestiaId: PlayerBestiaId,
    val position: Vec3L,
    val level: Int,
    val died: Boolean,
  ) : EntitySnapshot {

    /** The master's key, so selecting the master waits for this write before it reads the row. */
    override val writeKey: Any
      get() {
        return masterId
      }
  }

  override val kind = "player-bestia"
  override val loadsAtStartup = false

  override fun supports(world: World, id: EntityId): Boolean {
    return world.has(id, OwnedBestia::class)
  }

  override fun snapshot(world: World, id: EntityId): EntitySnapshot? {
    val owner = world.get(id, OwnedBestia::class) ?: return null
    val position = world.get(id, Position::class) ?: return null

    return Snapshot(
      entityId = id,
      masterId = owner.masterId,
      playerBestiaId = owner.playerBestiaId,
      position = position.toVec3L(),
      level = world.get(id, Level::class)?.level ?: 1,
      died = world.has(id, Dead::class),
    )
  }

  @Transactional
  override fun persist(snapshots: List<EntitySnapshot>) {
    for (snapshot in snapshots) {
      val snap = snapshot as Snapshot
      val playerBestia = playerBestiaRepository.findByIdForUpdate(snap.playerBestiaId)
      if (playerBestia == null) {
        LOG.debug { "Player bestia ${snap.playerBestiaId} was deleted, not persisting it" }
        continue
      }

      // A respawn brings it back alive, so it must not come back where it was killed.
      playerBestia.position = if (snap.died) {
        savePointService.forPlayerBestia(snap.playerBestiaId)
      } else {
        snap.position
      }
      playerBestia.level = snap.level
      playerBestiaRepository.save(playerBestia)
    }
  }

  /** Player bestias are respawned when their master is selected, not at startup. */
  override fun loadAll(world: World) = Unit

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** What [snapshot] reads; a system that snapshots a player bestia must declare these. */
    val SNAPSHOT_READS: ComponentClassSet = setOf(
      OwnedBestia::class, Position::class, Level::class, Dead::class,
    )
  }
}
