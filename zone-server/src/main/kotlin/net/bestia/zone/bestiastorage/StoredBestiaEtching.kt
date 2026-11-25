package net.bestia.zone.bestiastorage

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.battle.ecs.status.InCombat
import net.bestia.zone.crafting.StoredEtchable
import net.bestia.zone.crafting.StoredEtching
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.essence.Essence
import net.bestia.zone.master.bestia.BestiaEtching
import net.bestia.zone.master.bestia.PlayerBestiaLoss
import net.bestia.zone.util.EntityId
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.ConcurrentHashMap

/**
 * Runic Etching straight from the storage, under the storage window's rule: the Bestia Ledger, and no fight.
 *
 * [load] keeps what it read per master. It runs on the master's job key, like every storage change, so the copy
 * goes stale only by a summon or a release, and [isStillStored] asks the database again before anything is spent.
 */
@Component
class StoredBestiaEtching(
  private val masters: MasterRepository,
  private val playerBestias: PlayerBestiaRepository,
  private val ledger: BestiaLedgerScript,
  private val etching: BestiaEtching,
  private val loss: PlayerBestiaLoss,
  private val transactions: TransactionTemplate,
) : StoredEtching {

  private val loaded = ConcurrentHashMap<Long, Map<PlayerBestiaId, StoredEtchable>>()

  override fun accessRefusal(world: World, masterEntityId: EntityId): OpError? {
    return when {
      world.has(masterEntityId, InCombat::class) -> OpError.BESTIA_STORAGE_IN_COMBAT
      !ledger.grants(world, masterEntityId) -> OpError.BESTIA_STORAGE_NO_ACCESS
      else -> null
    }
  }

  override fun load(masterId: Long): List<StoredEtchable> {
    val stored = transactions.execute {
      masters.findByIdOrThrow(masterId).bestias.storedBestias.sortedBy { it.id }
        .map { StoredEtchable(it.id, it.bestia.id, it.level, it.name) }
    }.orEmpty()
    loaded[masterId] = stored.associateBy { it.playerBestiaId }

    return stored
  }

  override fun refusal(masterId: Long, playerBestiaId: PlayerBestiaId, gemItemId: Long): OpError? {
    val stored = loaded[masterId]?.get(playerBestiaId) ?: return OpError.CRAFT_NOT_POSSIBLE
    val tier = etching.tierOfGem(gemItemId) ?: return OpError.CRAFT_NOT_POSSIBLE

    return if (etching.fits(stored.speciesId, stored.level, tier)) null else OpError.CRAFT_NOT_POSSIBLE
  }

  override fun levelOf(masterId: Long, playerBestiaId: PlayerBestiaId): Int {
    return loaded[masterId]?.get(playerBestiaId)?.level ?: 0
  }

  override fun isStillStored(masterId: Long, playerBestiaId: PlayerBestiaId): Boolean {
    return transactions.execute {
      playerBestias.findByIdOrNull(playerBestiaId)?.let { it.master.id == masterId && it.isStored } == true
    } == true
  }

  override fun bind(masterId: Long, playerBestiaId: PlayerBestiaId, gemItemId: Long): Essence? {
    val stored = loaded[masterId]?.get(playerBestiaId) ?: return null
    val tier = etching.tierOfGem(gemItemId) ?: return null
    if (!isStillStored(masterId, playerBestiaId)) return null

    loss.loseStored(playerBestiaId)
    loaded.computeIfPresent(masterId) { _, bestias -> bestias - playerBestiaId }

    return Essence(bestiaId = stored.speciesId, tier = tier)
  }
}
