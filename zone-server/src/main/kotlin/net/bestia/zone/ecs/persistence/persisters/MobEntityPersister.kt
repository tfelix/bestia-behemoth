package net.bestia.zone.ecs.persistence.persisters

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.bestia.BestiaEntityFactory
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.bestia.BestiaVisual
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.persistence.EntityPersister
import net.bestia.zone.ecs.persistence.EntitySnapshot
import net.bestia.zone.entity.PersistedComponent
import net.bestia.zone.entity.PersistedEntity
import net.bestia.zone.entity.PersistedEntityRepository
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** Minimal mutable state of a world mob; static stats are re-derived from the bestia template on load. */
data class MobSnapshot(
  override val entityId: EntityId,
  val bestiaId: Long,
  val x: Long,
  val y: Long,
  val z: Long,
  val currentHp: Int,
) : EntitySnapshot

/**
 * Persists world mobs/NPCs (entities with a [BestiaVisual] but no [Account] — player bestias have
 * both) into the generic [PersistedEntity]/[PersistedComponent] blob tables, and rebuilds them on
 * startup through [BestiaEntityFactory] (which re-derives Health/Speed/AI from the bestia catalog),
 * overlaying the persisted current HP.
 */
@Component
class MobEntityPersister(
  private val repository: PersistedEntityRepository,
  private val bestiaEntityFactory: BestiaEntityFactory,
  private val objectMapper: ObjectMapper,
) : EntityPersister {

  override val kind = KIND
  override val loadsAtStartup = true

  override fun supports(world: World, id: EntityId): Boolean =
    world.has(id, BestiaVisual::class) && !world.has(id, Account::class)

  override fun snapshot(world: World, id: EntityId): EntitySnapshot? {
    val visual = world.get(id, BestiaVisual::class) ?: return null
    val pos = world.get(id, Position::class) ?: return null
    val hp = world.get(id, Health::class)
    return MobSnapshot(
      entityId = id,
      bestiaId = visual.id,
      x = pos.x, y = pos.y, z = pos.z,
      currentHp = hp?.current ?: NO_HP,
    )
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

  @Transactional(readOnly = true)
  override fun loadAll(world: World) {
    val rows = repository.findAllByKind(kind)
    var loaded = 0
    for (row in rows) {
      val json = row.components.firstOrNull()?.data ?: continue
      val snap = objectMapper.readValue<MobSnapshot>(json)
      bestiaEntityFactory.createMobEntity(
        world = world,
        bestiaId = snap.bestiaId,
        pos = Vec3L(snap.x, snap.y, snap.z),
        entityId = snap.entityId,
      )
      if (snap.currentHp != NO_HP) {
        world.modify(snap.entityId) { id ->
          get(id, Health::class)?.let {
            it.current = snap.currentHp // the setter marks Health dirty itself
          }
        }
      }
      loaded++
    }
    LOG.info { "Rehydrated $loaded persisted mob entities" }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val KIND = "mob"
    private const val NO_HP = -1
  }
}
