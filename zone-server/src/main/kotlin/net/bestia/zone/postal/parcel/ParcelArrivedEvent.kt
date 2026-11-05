package net.bestia.zone.postal.parcel

/** A parcel to [recipientMasterId] waits at [officeName]. Published after the commit. */
data class ParcelArrivedEvent(
  val recipientAccountId: Long,
  val recipientMasterId: Long,
  val parcelId: Long,
  val senderName: String,
  val officeName: String,
)
