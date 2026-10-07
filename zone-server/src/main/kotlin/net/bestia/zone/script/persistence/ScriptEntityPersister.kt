package net.bestia.zone.script.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.EntityPersister
import net.bestia.zone.persistence.EntitySnapshot
import net.bestia.zone.script.ecs.ScriptComponent
import net.bestia.zone.script.ecs.ScriptEntitySpawner
import net.bestia.zone.persistence.PersistedComponent
import net.bestia.zone.persistence.PersistedEntity
import net.bestia.zone.persistence.PersistedEntityRepository
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.MasterSpawnPointService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import net.bestia.zone.ecs.core.ComponentClassSet

/** Static state of a script entity - position plus the id of the script that governs it. */
data class ScriptEntitySnapshot(
  override val entityId: EntityId,
  val x: Long,
  val y: Long,
  val z: Long,
  val scriptId: String,
) : EntitySnapshot

/**
 * Persists [ScriptComponent] entities into the generic [PersistedEntity]/[PersistedComponent] blob
 * tables, and rebuilds them on startup through [ScriptEntitySpawner].
 *
 * [loadAll] does double duty: persisted script entities are rehydrated with their original entity ids, exactly
 * like [net.bestia.zone.spawn.persistence.MobEntityPersister], and every settlement spawn point that never had a
 * ward stone gets one (see [SPAWN_POINT_SCRIPT_ID]). A new stone is persisted immediately - not waiting for
 * [net.bestia.zone.persistence.EntityPersistenceService]'s periodic sweep - so a restart minutes after a fresh
 * world does not lose it.
 */
@Component
class ScriptEntityPersister(
  private val repository: PersistedEntityRepository,
  private val scriptEntitySpawner: ScriptEntitySpawner,
  private val masterSpawnPointService: MasterSpawnPointService,
  private val objectMapper: ObjectMapper,
) : EntityPersister {

  override val kind = ScriptComponent.KIND
  override val loadsAtStartup = true

  override val reads: ComponentClassSet = setOf(ScriptComponent::class, Position::class)

  override fun supports(world: World, id: EntityId): Boolean = world.has(id, ScriptComponent::class)

  override fun snapshot(world: World, id: EntityId): EntitySnapshot? {
    val script = world.get(id, ScriptComponent::class) ?: return null
    val pos = world.get(id, Position::class) ?: return null
    return ScriptEntitySnapshot(entityId = id, x = pos.x, y = pos.y, z = pos.z, scriptId = script.scriptId)
  }

  @Transactional
  override fun persist(snapshots: List<EntitySnapshot>) {
    if (snapshots.isEmpty()) return
    val existing = repository.findAllByEntityIdIn(snapshots.map { it.entityId }).associateBy { it.entityId }

    val rows = snapshots.map { snap ->
      val row = existing[snap.entityId] ?: PersistedEntity(entityId = snap.entityId, kind = kind)
      row.updatedAt = Instant.now()
      row.writeComponent(kind, objectMapper.writeValueAsString(snap))
      row
    }
    repository.saveAll(rows)
  }

  @Transactional
  override fun loadAll(world: World) {
    val rehydrated = repository.findAllByKind(kind).mapNotNull { row -> rehydrate(world, row) }
    LOG.info { "Rehydrated ${rehydrated.size} persisted script entities" }

    raiseMissingWards(world, standing = rehydrated.mapTo(HashSet()) { Vec3L(it.x, it.y, it.z) })
  }

  private fun rehydrate(world: World, row: PersistedEntity): ScriptEntitySnapshot? {
    val json = row.components.firstOrNull()?.data ?: return null
    val snap = objectMapper.readValue<ScriptEntitySnapshot>(json)
    scriptEntitySpawner.spawnScript(
      world = world,
      position = Vec3L(snap.x, snap.y, snap.z),
      scriptId = snap.scriptId,
      entityId = snap.entityId,
    )

    return snap
  }

  /** Each spawn point gets its ward once per world: a stone destroyed later is not raised again. */
  private fun raiseMissingWards(world: World, standing: Set<Vec3L>) {
    val neverWarded = masterSpawnPointService.ensureComputed().filterNot { it.wardRaised }
    if (neverWarded.isEmpty()) {
      return
    }

    val snapshots = neverWarded
      .filterNot { it.position in standing }
      .map { point -> raiseWard(world, point.position) }
    persist(snapshots)
    masterSpawnPointService.markWardRaised(neverWarded)

    LOG.info { "Raised ${snapshots.size} ward stone(s) at settlement spawn points" }
  }

  private fun raiseWard(world: World, position: Vec3L): ScriptEntitySnapshot {
    val id = scriptEntitySpawner.spawnScript(world = world, position = position, scriptId = SPAWN_POINT_SCRIPT_ID)

    return ScriptEntitySnapshot(
      entityId = id,
      x = position.x, y = position.y, z = position.z,
      scriptId = SPAWN_POINT_SCRIPT_ID,
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** scriptId carried by the placeholder entity created at each master spawn point candidate. */
    const val SPAWN_POINT_SCRIPT_ID = "master_spawn_ward"
  }
}
