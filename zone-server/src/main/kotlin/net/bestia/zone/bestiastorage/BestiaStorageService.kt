package net.bestia.zone.bestiastorage

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.StoredVitals
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.InCombat
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.battle.persistence.StatusEffectPersistenceService
import net.bestia.zone.battle.persistence.StatusEffectsSnapshot
import net.bestia.zone.breeding.BreedingFarms
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.bundle.ItemBundles
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.item.persistence.ItemInstance
import net.bestia.zone.master.bestia.OwnedBestiasPublisher
import net.bestia.zone.master.bestia.PlayerBestiaEntitySpawner
import net.bestia.zone.master.bestia.PlayerBestiaLoss
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.Teleporting
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * Summons, swaps and releases a master's stored bestia. Each request runs as a job keyed by the master, like every
 * save of its bestia and its items, so none of those can interleave with a bestia going in or out.
 */
@Service
class BestiaStorageService(
  private val connections: ConnectionInfoService,
  private val worldView: WorldView,
  private val ledger: BestiaLedgerScript,
  private val rows: BestiaStorageRows,
  private val views: BestiaStorageViews,
  private val spawner: PlayerBestiaEntitySpawner,
  private val statusEffects: StatusEffectPersistenceService,
  private val lootItems: LootItemEntitySpawner,
  private val loss: PlayerBestiaLoss,
  private val breedingFarms: BreedingFarms,
  private val ownedBestias: OwnedBestiasPublisher,
  private val out: OutMessageProcessor,
  private val asyncJobs: AsyncJobExecutor,
  private val clock: Clock = Clock.systemUTC(),
) {

  private class MasterSession(val accountId: AccountId, val masterId: Long, val masterEntityId: EntityId)

  /** An active bestia that may go into storage, as it stands now. */
  private class Leaving(val playerBestiaId: PlayerBestiaId, val position: Vec3L, val vitals: StoredVitals)

  fun open(accountId: AccountId) {
    onMaster(accountId) { session -> sendView(session) }
  }

  /** The stored bestia comes into a free active slot and lands next to its master. */
  fun summon(accountId: AccountId, playerBestiaId: PlayerBestiaId) {
    onMaster(accountId) { session ->
      val at = worldView.read { get(session.masterEntityId, Position::class)?.toVec3L() } ?: return@onMaster
      val vitals = rows.takeOut(session.masterId, playerBestiaId, at, clock.instant())
      val entityId = spawner.spawnPlayerBestia(playerBestiaId, vitals)
      worldView.modify(entityId) { id ->
        add(id, Teleporting(Teleporting.Destination.Follow(session.masterEntityId), secondsUntilMove = 0f))
      }

      changed(session)
    }
  }

  /**
   * The active bestia goes into storage and drops everything it carries in one bundle. The stored one appears
   * where it stood, so a swap never moves a bestia towards its master.
   */
  fun swap(accountId: AccountId, storedId: PlayerBestiaId, activeEntityId: EntityId) {
    onMaster(accountId) { session ->
      val leaving = worldView.read { leaving(this, session, activeEntityId) }
      rows.checkSwappable(session.masterId, storedId, clock.instant())

      val effects = worldView.read { statusEffects.snapshot(this, activeEntityId) }
      worldView.modify(activeEntityId) { id -> destroy(id) }
      connections.unregisterPlayerBestiaEntity(accountId, session.masterId, leaving.playerBestiaId)
      statusEffects.persist(listOf(effects ?: StatusEffectsSnapshot(activeEntityId, emptyList())))

      val swapped = try {
        rows.swap(session.masterId, storedId, leaving.playerBestiaId, leaving.position, leaving.vitals, clock.instant())
      } catch (e: Exception) {
        // Its row is still active: put it back as it was.
        spawner.spawnPlayerBestia(leaving.playerBestiaId, leaving.vitals)
        throw e
      }

      spawner.spawnPlayerBestia(storedId, swapped.vitals)
      swapped.bundle?.let { dropBundle(it, leaving.position, accountId) }
      changed(session)
    }
  }

  /** Gives up a stored bestia for good. */
  fun release(accountId: AccountId, playerBestiaId: PlayerBestiaId) {
    onMaster(accountId) { session ->
      rows.requireStored(session.masterId, playerBestiaId)
      loss.loseStored(playerBestiaId)
      changed(session)
    }
  }

  private fun onMaster(accountId: AccountId, action: (MasterSession) -> Unit) {
    val session = MasterSession(
      accountId = accountId,
      masterId = connections.getMasterId(accountId),
      masterEntityId = connections.getSelectedMasterEntityId(accountId),
    )

    asyncJobs.submit(key = session.masterId) {
      try {
        worldView.read { masterRefusal(this, session.masterEntityId) }?.let { throw BestiaStorageRefusal(it) }
        action(session)
      } catch (refusal: BestiaStorageRefusal) {
        LOG.debug { "Refused a bestia storage request of master ${session.masterId}: ${refusal.code}" }
        out.sendToPlayer(accountId, OperationErrorSMSG(refusal.code, refusal.args))
      }
    }
  }

  private fun masterRefusal(world: World, masterEntityId: EntityId): OpError? {
    if (world.has(masterEntityId, InCombat::class)) {
      return OpError.BESTIA_STORAGE_IN_COMBAT
    }
    if (!ledger.grants(world, masterEntityId)) {
      return OpError.BESTIA_STORAGE_NO_ACCESS
    }

    return null
  }

  private fun leaving(world: World, session: MasterSession, entityId: EntityId): Leaving {
    val owned = world.get(entityId, OwnedBestia::class)?.takeIf { it.masterId == session.masterId }
      ?: throw BestiaStorageRefusal(OpError.REQUEST_REFUSED)
    val refusal = when {
      world.has(entityId, Dead::class) -> OpError.BESTIA_STORAGE_BESTIA_DEAD
      world.has(entityId, InCombat::class) -> OpError.BESTIA_STORAGE_BESTIA_FIGHTING
      connections.getActiveEntityId(session.accountId) == entityId -> OpError.BESTIA_STORAGE_BESTIA_CONTROLLED
      breedingFarms.pairedWith(owned.playerBestiaId) != null -> OpError.BESTIA_STORAGE_BESTIA_BREEDING
      else -> null
    }
    refusal?.let { throw BestiaStorageRefusal(it) }

    val vitals = StoredVitals(
      health = world.getOrThrow(entityId, Health::class).current,
      mana = world.getOrThrow(entityId, Mana::class).current,
      stamina = world.getOrThrow(entityId, Stamina::class).current,
    )

    return Leaving(owned.playerBestiaId, world.getOrThrow(entityId, Position::class).toVec3L(), vitals)
  }

  private fun dropBundle(bundle: ItemInstance, at: Vec3L, ownerAccountId: AccountId) {
    worldView.read {
      lootItems.spawnLootItem(
        world = this,
        itemId = ItemBundles.ITEM_ID,
        amount = 1,
        pos = at,
        uniqueId = bundle.id,
        lootOwner = ownerAccountId,
        lootProtectionSeconds = BUNDLE_HEAD_START_SECONDS,
        heldWeight = bundle.heldWeight,
      )
    }
  }

  private fun changed(session: MasterSession) {
    ownedBestias.publish(session.accountId)
    sendView(session)
  }

  private fun sendView(session: MasterSession) {
    out.sendToPlayer(session.accountId, views.of(session.masterId, clock.instant()))
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    /** Long enough for a master far away to get there first. */
    const val BUNDLE_HEAD_START_SECONDS = 600f
  }
}
