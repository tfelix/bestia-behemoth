package net.bestia.zone.ecs.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.status.SkillPoints
import net.bestia.zone.ecs.battle.status.StatusPoints
import net.bestia.zone.ecs.bestia.BestiaVisual
import net.bestia.zone.ecs.item.ItemVisual
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Persists-then-removes entities tagged [PersistAndRemove] (added on disconnect). It dispatches
 * through the shared [EntityPersister] registry — the same strategies the periodic
 * [EntityPersistenceService] uses — so each entity kind is written to its own store. Persistence
 * happens synchronously here because the entity is destroyed immediately afterwards; disconnect is
 * infrequent so the brief tick-thread DB write is acceptable.
 */
@SpringComponent
@Order(90)
class PersistAndRemoveSystem(
  private val persisters: List<EntityPersister>
) : System {

  override val reads: ComponentClassSet = setOf(
    PersistAndRemove::class, Master::class, Account::class, Position::class,
    Level::class, SkillPoints::class, StatusPoints::class, BaseStatusValues::class,
    Health::class, BestiaVisual::class, ItemVisual::class,
  )

  override fun update(world: World, deltaTime: Float) {
    val toRemove = mutableListOf<EntityId>()
    world.query(PersistAndRemove::class).each { id -> toRemove.add(id) }
    if (toRemove.isEmpty()) return

    val byPersister = LinkedHashMap<EntityPersister, MutableList<EntitySnapshot>>()
    for (id in toRemove) {
      val persister = persisters.firstOrNull { it.supports(world, id) }
      if (persister == null) {
        LOG.warn { "Found no persistence handler for entity: $id, it will not be persisted" }
      } else {
        persister.snapshot(world, id)?.let { byPersister.getOrPut(persister) { mutableListOf() }.add(it) }
      }
      world.destroy(id)
    }

    byPersister.forEach { (persister, snapshots) -> persister.persist(snapshots) }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
