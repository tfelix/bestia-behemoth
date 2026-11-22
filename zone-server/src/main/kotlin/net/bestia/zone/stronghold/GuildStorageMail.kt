package net.bestia.zone.stronghold

import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.persistence.GuildStorageRepository
import net.bestia.zone.postal.parcel.ParcelService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Posts everything a sealed guild storage holds to the leader, as one parcel to their home post office. */
@Service
class GuildStorageMail(
  private val storages: GuildStorageRepository,
  private val parcels: ParcelService,
  private val inventoryService: InventoryService,
) {

  /** IO thread. One transaction, so the goods are either still in the storage or in the parcel. */
  @Transactional
  fun post(guildId: Long, leaderMasterId: Long, guildName: String) {
    val storage = storages.findByIdForUpdate(guildId) ?: return

    parcels.postInstant(leaderMasterId, guildName) { parcel -> inventoryService.moveBetweenEscrows(storage.container, parcel) }
  }
}
