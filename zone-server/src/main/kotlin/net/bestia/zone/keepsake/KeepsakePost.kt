package net.bestia.zone.keepsake

import net.bestia.zone.item.persistence.ItemInstanceRepository
import net.bestia.zone.postal.parcel.ParcelService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/** Sends a warded bag home once its ward's time is up. */
@Service
class KeepsakePost(
  private val itemInstances: ItemInstanceRepository,
  private val parcels: ParcelService,
  private val transactions: TransactionTemplate,
) {

  /** Off the tick. The bundle goes whole, and its master unpacks it at the post office. */
  fun sendHome(bundleId: Long, masterId: Long) {
    transactions.executeWithoutResult {
      val bundle = itemInstances.findByIdOrNull(bundleId) ?: return@executeWithoutResult
      parcels.postInstant(masterId, SENDER_NAME) { parcel -> parcel.addInstance(bundle) }
    }
  }

  private companion object {
    const val SENDER_NAME = "Keepsake Ward"
  }
}
