package net.bestia.zone.ecs.persistence.persisters

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.item.ItemVisual
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.persistence.EntityPersister
import net.bestia.zone.ecs.persistence.EntitySnapshot
import net.bestia.zone.entity.PersistedComponent
import net.bestia.zone.entity.PersistedEntity
import net.bestia.zone.entity.PersistedEntityRepository
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.loot.LootItemEntityFactory
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Persists dropped/ground item entities (those carrying an [ItemVisual]) into the generic blob
 * tables and rebuilds them on startup via [LootItemEntityFactory].
 */
@Component
class LootItemEntityPersister(
  private val repository: PersistedEntityRepository,
  private val lootItemEntityFactory: LootItemEntityFactory,
  private val objectMapper: ObjectMapper,
) : EntityPersister {

  override val kind = KIND
  override val loadsAtStartup = true

  override fun supports(world: World, id: EntityId): Boolean =
    world.has(id, ItemVisual::class)

  override fun snapshot(world: World, id: EntityId): EntitySnapshot? {
    val visual = world.get(id, ItemVisual::class) ?: return null
    val pos = world.get(id, Position::class) ?: return null

    return LootSnapshot(
      entityId = id,
      itemId = visual.itemId,
      amount = visual.amount,
      uniqueId = visual.playerItemId,
      x = pos.x, y = pos.y, z = pos.z,
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
      val snap = objectMapper.readValue<LootSnapshot>(json)

      lootItemEntityFactory.createLootEntity(
        world = world,
        itemId = snap.itemId,
        amount = snap.amount,
        pos = Vec3L(snap.x, snap.y, snap.z),
        playerItemUniqueId = snap.uniqueId,
        entityId = snap.entityId,
      )
      loaded++
    }
    LOG.info { "Rehydrated $loaded persisted ground item entities" }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val KIND = "loot"
  }
}
