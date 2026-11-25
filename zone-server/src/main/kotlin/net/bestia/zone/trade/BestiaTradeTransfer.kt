package net.bestia.zone.trade

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.account.persistence.StoredVitals
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.InCombat
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.battle.persistence.StatusEffectPersistenceService
import net.bestia.zone.battle.persistence.StatusEffectsSnapshot
import net.bestia.zone.crafting.station.StationWorkers
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.bundle.ItemBundles
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.master.bestia.PlayerBestiaEntitySpawner
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.Teleporting
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.travel.Journey
import net.bestia.zone.travel.JourneyOrder
import net.bestia.zone.util.EntityId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

/**
 * The world's half of trading a party bestia: what a trade shows of one, taking it out of its old owner's world
 * before the exchange, and putting it into its new owner's after. [TradeSettlement] is the database's half.
 *
 * Like [net.bestia.zone.bestiastorage.BestiaStorageService.swap], a bestia leaves the world before its row
 * changes, so no write-behind save of the live entity can land on a row that is no longer its old owner's.
 */
@Service
class BestiaTradeTransfer(
  private val worldView: WorldView,
  private val connections: ConnectionInfoService,
  private val playerBestias: PlayerBestiaRepository,
  private val pairedBestias: PairedBestias,
  private val spawner: PlayerBestiaEntitySpawner,
  private val statusEffects: StatusEffectPersistenceService,
  private val structures: PlayerStructureRegistry,
  private val stationWorkers: StationWorkers,
  private val lootItems: LootItemEntitySpawner,
) {

  /** Why a bestia cannot be offered, or the offer. */
  sealed interface Offer {
    data class Refused(val reason: Reason) : Offer
    data class Offered(val bestia: OfferedBestia) : Offer
  }

  enum class Reason { NOT_OWN, UNAVAILABLE }

  /** A bestia taken out of the world for the exchange. */
  class Leaving(
    val offer: OfferedBestia,
    val fromMasterId: Long,
    val vitals: StoredVitals,
  )

  /** Reads one of [masterId]'s party bestias the way a trade shows it. */
  fun describe(masterId: Long, entityId: EntityId): Offer {
    val live = worldView.read { liveState(this, masterId, entityId) } ?: return Offer.Refused(Reason.NOT_OWN)
    if (live.unavailable || pairedBestias.isPaired(live.owned.playerBestiaId)) {
      return Offer.Refused(Reason.UNAVAILABLE)
    }

    val row = playerBestias.findByIdOrNull(live.owned.playerBestiaId)?.takeIf { it.master.id == masterId && !it.isStored }
      ?: return Offer.Refused(Reason.NOT_OWN)

    return Offer.Offered(
      OfferedBestia(
        playerBestiaId = row.id,
        entityId = entityId,
        mobId = row.bestia.id.toInt(),
        name = row.name,
        level = live.level,
        sex = row.sex,
        origin = row.origin,
        originMasterName = row.originMasterName,
        ivs = row.individualValues,
        bag = live.bag,
      )
    )
  }

  /**
   * Takes every offered bestia out of the world, after checking each is still the one both sides saw: its old
   * owner's, alive, out of a fight and carrying what it carried. Takes none out when one is not.
   *
   * @return null when one of them changed
   */
  fun leave(offers: List<Pair<Long, OfferedBestia>>): List<Leaving>? {
    val checked = offers.map { (masterId, offer) ->
      val live = worldView.read { liveState(this, masterId, offer.entityId) }
      if (live == null || live.unavailable || pairedBestias.isPaired(offer.playerBestiaId) ||
        live.owned.playerBestiaId != offer.playerBestiaId || live.bag != offer.bag) {
        LOG.info { "Bestia ${offer.playerBestiaId} offered by master $masterId changed before the trade settled" }
        return null
      }
      Triple(masterId, offer, live)
    }

    return checked.map { (masterId, offer, live) ->
      val effects = worldView.read { statusEffects.snapshot(this, offer.entityId) }
      worldView.modify(offer.entityId) { id -> destroy(id) }
      connections.unregisterPlayerBestiaEntity(live.accountId, masterId, offer.playerBestiaId)
      statusEffects.persist(listOf(effects ?: StatusEffectsSnapshot(offer.entityId, emptyList())))

      Leaving(offer, masterId, live.vitals)
    }
  }

  /** Puts bestias that left for a trade that then failed back into their old owner's world, as they were. */
  fun comeBack(leaving: List<Leaving>) {
    leaving.forEach { spawner.spawnPlayerBestia(it.offer.playerBestiaId, it.vitals) }
  }

  /**
   * Brings each bestia to its new owner: into the world next to them, or, for one that went into storage, drops
   * what it carried at their feet.
   *
   * @param receiverEntityOf the master entity of the master id the bestia went to
   */
  fun arrive(arrivals: List<TradeSettlement.Arrival>, receiverEntityOf: (Long) -> EntityId) {
    for (arrival in arrivals) {
      val playerBestiaId = arrival.move.playerBestiaId
      val receiverEntityId = receiverEntityOf(arrival.move.toMasterId)

      // Its old owner's station work is not its new owner's; the spawn would otherwise put it back there.
      structures.workplaceOf(playerBestiaId)?.let { station -> worldView.read { stationWorkers.unassign(this, station.id) } }

      if (arrival.stored) {
        arrival.bundle?.let { dropAt(it.id, it.heldWeight, receiverEntityId) }
        continue
      }

      val entityId = spawner.spawnPlayerBestia(playerBestiaId, arrival.move.vitals)
      worldView.modify(entityId) { id ->
        add(id, Teleporting(Teleporting.Destination.Follow(receiverEntityId), secondsUntilMove = 0f))
      }
    }
  }

  private fun dropAt(bundleId: Long, heldWeight: Int?, receiverEntityId: EntityId) {
    worldView.read {
      val at = get(receiverEntityId, Position::class)?.toVec3L() ?: return@read
      lootItems.spawnLootItem(
        world = this,
        itemId = ItemBundles.ITEM_ID,
        amount = 1,
        pos = at,
        uniqueId = bundleId,
        lootOwner = get(receiverEntityId, Account::class)?.accountId,
        lootProtectionSeconds = BUNDLE_HEAD_START_SECONDS,
        heldWeight = heldWeight,
      )
    }
  }

  private class LiveState(
    val owned: OwnedBestia,
    val accountId: Long,
    val level: Int,
    val unavailable: Boolean,
    val bag: OfferedBestia.CarriedItems,
    val vitals: StoredVitals,
  )

  /** Null unless [entityId] is a live party bestia of [masterId]. */
  private fun liveState(world: World, masterId: Long, entityId: EntityId): LiveState? {
    val owned = world.get(entityId, OwnedBestia::class)?.takeIf { it.masterId == masterId } ?: return null
    val accountId = world.get(entityId, Account::class)?.accountId ?: return null
    val unavailable = world.has(entityId, Dead::class) || world.has(entityId, InCombat::class) ||
      world.get(entityId, Journey::class)?.order == JourneyOrder.PLAYER

    return LiveState(
      owned = owned,
      accountId = accountId,
      level = world.get(entityId, Level::class)?.level ?: 1,
      unavailable = unavailable,
      bag = carried(world, entityId),
      vitals = StoredVitals(
        health = world.get(entityId, Health::class)?.current ?: 1,
        mana = world.get(entityId, Mana::class)?.current ?: 0,
        stamina = world.get(entityId, Stamina::class)?.current ?: 0,
      ),
    )
  }

  private fun carried(world: World, entityId: EntityId): OfferedBestia.CarriedItems {
    val lines = world.get(entityId, Inventory::class)?.getItems().orEmpty()
      .mapTo(HashSet()) { "${it.itemId}:${it.uniqueId}:${it.amount}" }
    val wearsGear = world.get(entityId, Equipment::class)?.getWorn()?.isNotEmpty() ?: false

    return OfferedBestia.CarriedItems(lines, wearsGear)
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    /** As a swapped-out bestia's bundle: long enough for its new owner to pick it up first. */
    const val BUNDLE_HEAD_START_SECONDS = 600f
  }
}
