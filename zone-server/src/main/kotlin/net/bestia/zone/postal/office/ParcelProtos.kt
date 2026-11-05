package net.bestia.zone.postal.office

import net.bestia.bnet.proto.ParcelProto
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.postal.parcel.ParcelService

internal fun ParcelService.ParcelView.toProto(): ParcelProto.Parcel {
  return ParcelProto.Parcel.newBuilder()
    .setParcelId(parcelId)
    .setSenderName(senderName)
    .setDestinationName(destinationName)
    .setLatestArrivalEpochSeconds(latestArrival.epochSecond)
    .setArrived(arrived)
    .setCollectableHere(collectableHere)
    .setReturning(returning)
    .addAllGoods(goods.map { it.toParcelGoods() })
    .build()
}

private fun ReservedItem.toParcelGoods(): ParcelProto.ParcelGoods {
  return ParcelProto.ParcelGoods.newBuilder()
    .setItemId(itemId)
    .setAmount(amount)
    .setItemLevel(itemLevel)
    .setUpgradeLevel(upgradeLevel)
    .setDurability(durability)
    .setMaxDurability(maxDurability)
    .setSlots(slots)
    .addAllRolledStats(rolledStats.map { it.toProto() })
    .build()
}
