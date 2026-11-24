package net.bestia.zone.bestiastorage

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.PlayerBestiaPolicy
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.account.persistence.StoredVitals
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.bundle.ItemBundles
import net.bestia.zone.item.persistence.ItemInstance
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/** The database half of the bestia storage. Every refusal is a [BestiaStorageRefusal] and changes nothing. */
@Service
class BestiaStorageRows(
  private val playerBestias: PlayerBestiaRepository,
  private val policy: PlayerBestiaPolicy,
  private val bundles: ItemBundles,
) {

  class Swapped(
    /** The pools the stored bestia comes out with. */
    val vitals: StoredVitals?,
    /** What the active bestia carried, or null when it carried nothing. */
    val bundle: ItemInstance?,
  )

  /**
   * Takes a stored bestia out into a free active slot at [at].
   *
   * @return the pools it comes out with
   */
  @Transactional
  fun takeOut(masterId: Long, playerBestiaId: PlayerBestiaId, at: Vec3L, now: Instant): StoredVitals? {
    val stored = storedRow(masterId, playerBestiaId, now)
    if (!policy.hasFreeActiveSlot(stored.master)) {
      throw BestiaStorageRefusal(OpError.BESTIA_STORAGE_PARTY_FULL)
    }

    stored.position = at

    return stored.takeOutOfStorage()
  }

  /** Refuses a swap before anything leaves the world for it. */
  @Transactional(readOnly = true)
  fun checkSwappable(masterId: Long, storedId: PlayerBestiaId, now: Instant) {
    val stored = storedRow(masterId, storedId, now)
    // The client offers a swap only with every active slot taken.
    if (policy.hasFreeActiveSlot(stored.master)) {
      throw BestiaStorageRefusal(OpError.REQUEST_REFUSED)
    }
  }

  /**
   * Puts the active bestia into storage with [activeVitals] and its items packed into a bundle, and takes the
   * stored one out where the active one stood, at [at].
   */
  @Transactional
  fun swap(
    masterId: Long,
    storedId: PlayerBestiaId,
    activeId: PlayerBestiaId,
    at: Vec3L,
    activeVitals: StoredVitals,
    now: Instant,
  ): Swapped {
    val stored = storedRow(masterId, storedId, now)
    val active = playerBestias.findByIdForUpdate(activeId)?.takeIf { it.master.id == masterId && !it.isStored }
      ?: throw BestiaStorageRefusal(OpError.REQUEST_REFUSED)

    active.position = at
    active.store(now, restsUntil = now.plus(REST), activeVitals)
    val bundle = bundles.packAll(active.container)

    stored.position = at
    val vitals = stored.takeOutOfStorage()

    return Swapped(vitals, bundle)
  }

  /** @throws BestiaStorageRefusal unless [playerBestiaId] is one of the master's stored bestia */
  @Transactional(readOnly = true)
  fun requireStored(masterId: Long, playerBestiaId: PlayerBestiaId) {
    storedRowOf(masterId, playerBestiaId)
  }

  /** A stored bestia of the master's that has rested long enough to come out. */
  private fun storedRow(masterId: Long, playerBestiaId: PlayerBestiaId, now: Instant): PlayerBestia {
    val stored = storedRowOf(masterId, playerBestiaId)
    val restsUntil = stored.restsUntil
    if (restsUntil != null && restsUntil.isAfter(now)) {
      val minutesLeft = Duration.between(now, restsUntil).toSeconds().let { (it + 59) / 60 }
      throw BestiaStorageRefusal(OpError.BESTIA_STORAGE_RESTING, listOf(minutesLeft.toString()))
    }

    return stored
  }

  private fun storedRowOf(masterId: Long, playerBestiaId: PlayerBestiaId): PlayerBestia {
    // A bestia not stored or not the master's: the window only offers the master's stored ones.
    return playerBestias.findByIdForUpdate(playerBestiaId)?.takeIf { it.master.id == masterId && it.isStored }
      ?: throw BestiaStorageRefusal(OpError.REQUEST_REFUSED)
  }

  companion object {
    /** How long a bestia that went into storage stays there, so swaps cannot move it about. */
    val REST: Duration = Duration.ofMinutes(30)
  }
}
