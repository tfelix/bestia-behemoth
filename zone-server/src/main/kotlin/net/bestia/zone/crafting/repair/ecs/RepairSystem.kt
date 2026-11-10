package net.bestia.zone.crafting.repair.ecs

import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.HpRegenSystem
import net.bestia.zone.crafting.repair.RepairService
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.ecs.wear.StructureWear
import net.bestia.zone.settlement.ecs.CitySign
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/**
 * Puts health back into every structure somebody is repairing, as fast as the work goes and as far as the paid-in
 * materials reach. A repairer who walks off, or whose structure is whole or left the town, stops.
 */
@SpringComponent
class RepairSystem(
  private val repairs: RepairService,
  private val structures: PlayerStructureRegistry,
) : System {

  // With the other health that comes back over time, after regeneration so the two do not race.
  override val phase = Phase.RECOVERY
  override val after = setOf(HpRegenSystem::class)

  override val reads: ComponentClassSet = setOf(
    Position::class, PropPose::class, StaticVisual::class, PlayerStructureIdentity::class, CitySign::class,
    StructureWear::class, Dead::class
  )
  override val writes: ComponentClassSet = setOf(Repairing::class, Health::class)

  override fun update(world: World, deltaTime: Float) {
    val stopped = mutableListOf<EntityId>()

    world.query(Repairing::class).each { workerId ->
      val repairing = get<Repairing>()
      if (!work(world, workerId, repairing, deltaTime)) stopped += workerId
    }

    stopped.forEach { world.remove(it, Repairing::class) }
  }

  /** @return false when the work is over, for whatever reason */
  private fun work(world: World, workerId: EntityId, repairing: Repairing, deltaTime: Float): Boolean {
    val targetId = repairing.targetEntityId
    if (!world.isAlive(targetId) || world.has(workerId, Dead::class) || world.has(targetId, Dead::class)) return false

    val target = repairs.targetOf(world, targetId) ?: return false
    val workerAt = world.get(workerId, Position::class)?.toVec3L() ?: return false
    if (!repairs.isInReach(world, workerAt, targetId, target) || !repairs.isInStandingTown(target)) return false

    val health = world.get(targetId, Health::class) ?: return false
    // A building only shows its stored damage once StructureWearSystem has put it back on.
    if (target is RepairService.Target.Building && !world.has(targetId, StructureWear::class)) return true
    if (health.current >= health.max) return false

    val cost = repairs.costOf(world, targetId, target) ?: return false
    repairing.pendingHp += cost.hpPerSecond * deltaTime
    val stock = target.stockHp ?: Int.MAX_VALUE
    val restored = minOf(repairing.pendingHp.toInt(), health.max - health.current, stock)
    if (restored <= 0) return stock > 0
    repairing.pendingHp -= restored

    health.current += restored
    if (target is RepairService.Target.Building) {
      structures.setWear(target.structureId, health.max - health.current, stock - restored)
    }

    return health.current < health.max && stock - restored > 0
  }
}
