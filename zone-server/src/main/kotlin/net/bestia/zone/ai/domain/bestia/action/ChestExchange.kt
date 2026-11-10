package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.domain.bestia.JobChest
import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.container.ItemHandover.Party
import net.bestia.zone.item.ecs.ChestStorage
import net.bestia.zone.item.ecs.ExchangeReach
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.util.EntityId

/** A bestia standing at its job chest, close enough to swap goods with it as its master could. */
class ChestExchange(
  val masterId: Long,
  val bestia: Party,
  val chest: Party,
  val bag: Inventory,
  val contents: Inventory,
) {

  companion object {
    /** How close to walk: inside [ExchangeReach.HORIZONTAL], with a tile to spare for the chest's own footprint. */
    const val REACH = ExchangeReach.HORIZONTAL - 1

    val READS: ComponentClassSet = setOf(ChestStorage::class, PropPose::class, Master::class, OwnedBestia::class)

    /** Null when the chest's prop is not in the world, is not its master's, or stands out of reach. */
    fun open(world: World, aoi: EntityAOIService, bestiaId: EntityId, chest: JobChest): ChestExchange? {
      val chestId = aoi.queryEntitiesInCube(chest.position, 2, AoiLayer.STATIC_ONLY)
        .firstOrNull { world.get(it, ChestStorage::class)?.structureId == chest.structureId }
        ?: return null
      val masterId = world.get(bestiaId, OwnedBestia::class)?.masterId ?: return null
      val bestiaHolder = ItemOwner.holderOf(world, bestiaId, masterId) ?: return null
      val chestHolder = ItemOwner.holderOf(world, chestId, masterId) ?: return null
      if (!ExchangeReach.within(world, bestiaId, chestId)) return null

      val bag = world.get(bestiaId, Inventory::class) ?: return null
      val contents = world.get(chestId, Inventory::class) ?: return null

      return ChestExchange(masterId, Party(bestiaId, bestiaHolder), Party(chestId, chestHolder), bag, contents)
    }
  }
}
