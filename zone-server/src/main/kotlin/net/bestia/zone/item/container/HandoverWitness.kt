package net.bestia.zone.item.container

/** Told of every handover that reached the database, on the IO thread, after it committed. */
fun interface HandoverWitness {
  fun handedOver(masterId: Long, from: ItemHolder, to: ItemHolder, itemId: Long, amount: Int)
}
