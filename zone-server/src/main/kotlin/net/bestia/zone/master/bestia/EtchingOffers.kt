package net.bestia.zone.master.bestia

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.crafting.EtchingOffer
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.master.bestia.ecs.OfferedForEtching
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * A master lets any Artificer at a Carving Table etch one of their Bestias: they stand it at the table and offer it.
 * Fee and gem change hands in an ordinary trade beforehand. The Essence Gem goes to the owner.
 */
@Component
class EtchingOffers(
  private val structures: PlayerStructureService,
  private val connections: ConnectionInfoService,
  private val outMessageProcessor: OutMessageProcessor,
) {

  /** @return why [bestiaId] cannot be offered by [actorId], or null once it is */
  fun offer(world: World, actorId: EntityId, bestiaId: EntityId): OpError? {
    val ownerMasterId = world.get(bestiaId, OwnedBestia::class)?.masterId
    if (ownerMasterId == null || ItemOwner.of(world, actorId)?.masterId != ownerMasterId || world.has(bestiaId, Dead::class)) {
      return OpError.REQUEST_REFUSED
    }
    if (!atTable(world, bestiaId)) {
      return OpError.CRAFT_BESTIA_NOT_AT_TABLE
    }

    world.add(bestiaId, OfferedForEtching)
    world.get(actorId, Account::class)?.let {
      outMessageProcessor.sendToPlayer(it.accountId, OperationSuccessSMSG(OpSuccess.ETCHING_OFFERED))
    }

    return null
  }

  fun isOffered(world: World, bestiaId: EntityId): Boolean {
    return world.has(bestiaId, OfferedForEtching::class) && atTable(world, bestiaId)
  }

  fun atTable(world: World, bestiaId: EntityId): Boolean {
    val position = world.get(bestiaId, Position::class)?.toVec3L() ?: return false

    return structures.stationNear(world, position, StaticEntityKind.CARVING_TABLE) != null
  }

  /** Other masters' Bestias offered at the table at [tablePosition], for the picker of [etcherMasterId]. */
  fun offersAt(world: World, tablePosition: Vec3L, etcherMasterId: Long): List<EtchingOffer> {
    val offers = mutableListOf<EtchingOffer>()
    world.each(OfferedForEtching::class) { bestiaId, _ ->
      val owned = world.get(bestiaId, OwnedBestia::class) ?: return@each
      val position = world.get(bestiaId, Position::class)?.toVec3L() ?: return@each
      if (owned.masterId == etcherMasterId || position.distance(tablePosition) > TABLE_REACH) return@each

      val owner = ownerOf(world, bestiaId)?.let { world.get(it, Master::class) } ?: return@each
      val speciesId = world.get(bestiaId, EntityVisual::class)?.id ?: return@each
      offers += EtchingOffer(bestiaId, speciesId, world.get(bestiaId, Level::class)?.level ?: 0, owner.name)
    }

    return offers
  }

  /** The owner's master entity, or null when the owner is no longer online to receive anything. */
  fun ownerOf(world: World, bestiaId: EntityId): EntityId? {
    val accountId = world.get(bestiaId, Account::class)?.accountId ?: return null

    return runCatching { connections.getSelectedMasterEntityId(accountId) }.getOrNull()
  }

  companion object {
    /** A Bestia counts as at a table within the reach `stationNear` searches. */
    private const val TABLE_REACH = PlayerStructureService.RANGE_TILES * 2

    val READS: ComponentClassSet = setOf(
      OfferedForEtching::class, Position::class, StaticVisual::class, PlayerStructureIdentity::class
    )
  }
}
