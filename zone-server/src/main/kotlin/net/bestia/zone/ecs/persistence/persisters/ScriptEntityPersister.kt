package net.bestia.zone.ecs.persistence.persisters

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.persistence.EntityPersister
import net.bestia.zone.ecs.persistence.EntitySnapshot
import net.bestia.zone.ecs.script.ScriptComponent
import net.bestia.zone.ecs.script.ScriptEntityFactory
import net.bestia.zone.entity.PersistedComponent
import net.bestia.zone.entity.PersistedEntity
import net.bestia.zone.entity.PersistedEntityRepository
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.MasterSpawnPointService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

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
 * tables, and rebuilds them on startup through [ScriptEntityFactory].
 *
 * [loadAll] does double duty: if script entities are already persisted (a normal restart), they are
 * rehydrated with their original entity ids, exactly like [MobEntityPersister]. If none exist yet (the
 * world was just created), it asks [MasterSpawnPointService] for the settlement spawn point
 * candidates, creates one placeholder script entity per candidate (see [SPAWN_POINT_SCRIPT_ID]), and
 * persists them immediately - not waiting for
 * [net.bestia.zone.ecs.persistence.EntityPersistenceService]'s periodic sweep - so a restart minutes
 * after a fresh world does not lose them.
 */
@Component
class ScriptEntityPersister(
  private val repository: PersistedEntityRepository,
  private val scriptEntityFactory: ScriptEntityFactory,
  private val masterSpawnPointService: MasterSpawnPointService,
  private val objectMapper: ObjectMapper,
) : EntityPersister {

  override val kind = ScriptComponent.KIND
  override val loadsAtStartup = true

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
      row.replaceComponents(
        listOf(PersistedComponent(type = kind, data = objectMapper.writeValueAsString(snap)))
      )
      row
    }
    repository.saveAll(rows)
  }

  @Transactional
  override fun loadAll(world: World) {
    val rows = repository.findAllByKind(kind)
    if (rows.isNotEmpty()) {
      var loaded = 0
      for (row in rows) {
        val json = row.components.firstOrNull()?.data ?: continue
        val snap = objectMapper.readValue<ScriptEntitySnapshot>(json)
        scriptEntityFactory.createScriptEntity(
          world = world,
          position = Vec3L(snap.x, snap.y, snap.z),
          scriptId = snap.scriptId,
          entityId = snap.entityId,
        )
        loaded++
      }
      LOG.info { "Rehydrated $loaded persisted script entities" }
      return
    }

    val spawnPoints = masterSpawnPointService.ensureComputed()
    val snapshots = spawnPoints.map { point ->
      val id = scriptEntityFactory.createScriptEntity(
        world = world,
        position = point.position,
        scriptId = SPAWN_POINT_SCRIPT_ID,
      )
      ScriptEntitySnapshot(
        entityId = id,
        x = point.position.x, y = point.position.y, z = point.position.z,
        scriptId = SPAWN_POINT_SCRIPT_ID,
      )
    }
    persist(snapshots)
    LOG.info { "Created and persisted ${snapshots.size} script entities from settlement spawn points" }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** scriptId carried by the placeholder entity created at each master spawn point candidate. */
    const val SPAWN_POINT_SCRIPT_ID = "master_spawn_ward"
  }
}
