package net.bestia.zone.prop.ecs.wear

import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.persistence.EntityPersistenceSystem
import net.bestia.zone.persistence.PersistAndRemoveSystem
import net.bestia.zone.prop.PlayerStructureRegistry
import org.springframework.stereotype.Component as SpringComponent

/**
 * Keeps a player structure's damage across the column being unloaded. A static prop gets full health when it is
 * promoted; this takes the stored damage off it once, and from then on stores what it lacks.
 */
@SpringComponent
class StructureWearSystem(
  private val structures: PlayerStructureRegistry,
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(1f)
  // Before the entities leaving the world are written back, so a structure unloaded this tick keeps its damage.
  override val phase = Phase.PERSIST
  override val before = setOf(EntityPersistenceSystem::class, PersistAndRemoveSystem::class)

  override val reads: ComponentClassSet = setOf(PlayerStructureIdentity::class, PropPose::class, Dead::class)
  override val writes: ComponentClassSet = setOf(Health::class, StructureWear::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(PlayerStructureIdentity::class, Health::class, PropPose::class).each { id ->
      if (world.has(id, Dead::class)) return@each
      val entry = structures.of(get<PlayerStructureIdentity>().structureId) ?: return@each
      val health = get<Health>()

      if (!world.has(id, StructureWear::class)) {
        health.current = (health.current - entry.missingHp).coerceAtLeast(1)
        world.add(id, StructureWear())
      }

      structures.setWear(entry.id, health.max - health.current, entry.repairStockHp)
    }
  }
}
