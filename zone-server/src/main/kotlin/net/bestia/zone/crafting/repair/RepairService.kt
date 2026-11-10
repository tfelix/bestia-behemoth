package net.bestia.zone.crafting.repair

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.CombatTargetPromotion
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.crafting.repair.ecs.Repairing
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.prop.PropFootprint
import net.bestia.zone.prop.ecs.construction.ConstructionSite
import net.bestia.zone.settlement.TownRegistry
import net.bestia.zone.settlement.TownService
import net.bestia.zone.settlement.ecs.CitySign
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/**
 * Repairs inside a player town: the repairer pays a share of the structure's kit into it, then works on it like
 * a construction site. See the design docs' player settlements page. Tick thread only.
 */
@Service
class RepairService(
  private val kits: KitCatalogue,
  private val structures: PlayerStructureRegistry,
  private val towns: TownRegistry,
  private val promotion: CombatTargetPromotion,
  private val inventoryService: InventoryService,
  private val jobs: AsyncJobExecutor,
  private val worldService: WorldService,
) {

  /** What [targetId] is to a repair, or null when it is nothing a player can repair. */
  sealed interface Target {
    val position: Vec3L

    /** Health the materials paid in can still restore; null for the sign, which needs no materials. */
    val stockHp: Int?

    data class Sign(override val position: Vec3L, val townId: Long) : Target {
      override val stockHp: Int? = null
    }

    data class Building(override val position: Vec3L, val structureId: Long, override val stockHp: Int) : Target
  }

  /** Starts [actorId] repairing [targetId], or stops it if it already was. */
  fun toggle(world: World, actorId: EntityId, targetId: EntityId): OpError? {
    if (world.get(actorId, Repairing::class)?.targetEntityId == targetId) {
      world.remove(actorId, Repairing::class)
      return null
    }

    val target = targetOf(world, targetId) ?: return OpError.REQUEST_REFUSED
    val actorAt = world.get(actorId, Position::class)?.toVec3L() ?: return OpError.BUILD_OUT_OF_RANGE
    if (!isInReach(world, actorAt, targetId, target)) return OpError.BUILD_OUT_OF_RANGE
    if (!isInStandingTown(target)) return OpError.REPAIR_OUTSIDE_TOWN

    val missingHp = missingHpOf(world, targetId, target)
    if (missingHp <= 0) return OpError.REPAIR_NOT_NEEDED

    if (target is Target.Building) {
      val stock = payIn(world, actorId, target, missingHp)
      if (stock <= 0) return OpError.REPAIR_NEEDS_MATERIALS
    }

    if (!promotion.promoteIfNeeded(world, targetId, actorAt, PlayerStructureService.RANGE_TILES * 4)) {
      return OpError.REQUEST_REFUSED
    }
    world.add(actorId, Repairing(targetId))

    return null
  }

  fun targetOf(world: World, targetId: EntityId): Target? {
    world.get(targetId, CitySign::class)?.let { sign ->
      val at = world.get(targetId, Position::class)?.toVec3L() ?: return null
      return Target.Sign(at, sign.townId)
    }

    if (world.has(targetId, ConstructionSite::class)) return null
    val structureId = world.get(targetId, PlayerStructureIdentity::class)?.structureId ?: return null
    val entry = structures.of(structureId) ?: return null

    return Target.Building(entry.position, structureId, entry.repairStockHp)
  }

  fun isInStandingTown(target: Target): Boolean {
    return when (target) {
      is Target.Sign -> towns.of(target.townId)?.isStanding == true
      is Target.Building -> towns.standingAt(target.position) != null
    }
  }

  fun isInReach(world: World, actorAt: Vec3L, targetId: EntityId, target: Target): Boolean {
    val pose = world.get(targetId, PropPose::class)
    val visual = world.get(targetId, StaticVisual::class)
    val distance = if (pose != null && visual != null) {
      PropFootprint.distanceTo(actorAt, pose, visual, worldService.config.voxelSize)
    } else {
      actorAt.distance(target.position).toDouble()
    }

    return distance <= PlayerStructureService.RANGE_TILES
  }

  /** What a repair of [target] costs: the sign needs only work, as long as its site took. */
  fun costOf(world: World, targetId: EntityId, target: Target): RepairCost? {
    return when (target) {
      is Target.Sign -> {
        val maxHp = world.get(targetId, Health::class)?.max ?: return null
        RepairCost(maxHp, TownService.SIGN_BUILD_SECONDS, emptyMap())
      }
      is Target.Building -> structures.of(target.structureId)?.let { kits.repairCostOf(it.kind) }
    }
  }

  private fun missingHpOf(world: World, targetId: EntityId, target: Target): Int {
    val health = world.get(targetId, Health::class)
    if (health != null) return health.max - health.current

    return (target as? Target.Building)?.let { structures.of(it.structureId)?.missingHp } ?: 0
  }

  /** Pays in what the repairer carries towards the health not yet paid for. @return the stock now held */
  private fun payIn(world: World, actorId: EntityId, target: Target.Building, missingHp: Int): Int {
    val entry = structures.of(target.structureId) ?: return 0
    val unpaidHp = missingHp - entry.repairStockHp
    if (unpaidHp <= 0) return entry.repairStockHp

    val cost = kits.repairCostOf(entry.kind) ?: return 0
    if (cost.materials.isEmpty()) {
      structures.setWear(entry.id, entry.missingHp, entry.repairStockHp + unpaidHp)
      return entry.repairStockHp + unpaidHp
    }

    val inventory = world.get(actorId, Inventory::class) ?: return entry.repairStockHp
    val held = cost.materials.keys.associateWith { itemId ->
      inventory.getItems().filter { it.itemId == itemId && !it.equipped }.sumOf { it.amount }
    }
    val deposit = cost.deposit(unpaidHp, held)
    if (deposit.hp == 0) return entry.repairStockHp

    take(world, actorId, inventory, deposit.taken)
    val stock = entry.repairStockHp + deposit.hp
    structures.setWear(entry.id, entry.missingHp, stock)

    return stock
  }

  private fun take(world: World, actorId: EntityId, inventory: Inventory, taken: Map<Long, Int>) {
    taken.forEach { (itemId, amount) -> inventory.takeAcrossPiles(itemId, amount) }
    val owner = ItemOwner.of(world, actorId) ?: return

    jobs.submit(key = owner.masterId) {
      if (!inventoryService.consumeAll(owner.masterId, taken.map { it.key to it.value }, owner.playerBestiaId)) {
        LOG.warn { "Master ${owner.masterId} paid $taken into a repair the database did not have" }
      }
    }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
