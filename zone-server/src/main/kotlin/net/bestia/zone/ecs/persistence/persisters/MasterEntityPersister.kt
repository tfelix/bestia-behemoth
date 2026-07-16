package net.bestia.zone.ecs.persistence.persisters

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.ecs.account.Master as MasterComponent
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.status.SkillPoints
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.persistence.EntityPersister
import net.bestia.zone.ecs.persistence.EntitySnapshot
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Mutable master state written back to the dedicated relational `master` table. */
data class MasterSnapshot(
  override val entityId: EntityId,
  val masterId: Long,
  val x: Long,
  val y: Long,
  val z: Long,
  val level: Int,
  val skillPoints: Int,
) : EntitySnapshot

/**
 * Persists online player masters back into their dedicated `master` table (the same fields the old
 * `PersistAndRemoveSystem` hand-mapped). Masters are loaded on login via `MasterEntityFactory`, so
 * this persister does not participate in startup rehydration ([loadsAtStartup] = false).
 */
@Component
class MasterEntityPersister(
  private val masterRepository: MasterRepository,
) : EntityPersister {

  override val kind = "master"
  override val loadsAtStartup = false

  override fun supports(world: World, id: EntityId): Boolean =
    world.has(id, MasterComponent::class)

  override fun snapshot(world: World, id: EntityId): EntitySnapshot? {
    val master = world.get(id, MasterComponent::class) ?: return null
    val pos = world.get(id, Position::class) ?: return null
    val level = world.get(id, Level::class)?.level ?: 1
    val skillPoints = world.get(id, SkillPoints::class)?.value ?: 0
    return MasterSnapshot(
      entityId = id,
      masterId = master.masterId,
      x = pos.x, y = pos.y, z = pos.z,
      level = level,
      skillPoints = skillPoints,
    )
  }

  @Transactional
  override fun persist(snapshots: List<EntitySnapshot>) {
    for (snapshot in snapshots) {
      val snap = snapshot as MasterSnapshot
      val master = masterRepository.findByIdOrNull(snap.masterId)
      if (master == null) {
        LOG.warn { "Master ${snap.masterId} was not found, cannot persist it" }
        continue
      }
      master.currentPosition = Vec3L(snap.x, snap.y, snap.z)
      master.level = snap.level
      master.skillPoints = snap.skillPoints
      masterRepository.save(master)
      LOG.debug { "Persisted master ${master.id} at ${master.currentPosition} (level ${master.level})" }
    }
  }

  /** Masters are rehydrated on demand at login, not at startup. */
  override fun loadAll(world: World) = Unit

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
