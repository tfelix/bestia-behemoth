package net.bestia.zone.postal.persistence

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import net.bestia.zone.item.persistence.ItemContainer
import java.time.Instant

/**
 * Goods on their way to a master's post office, or waiting there. The goods sit in [container], which no player
 * holds, until the recipient collects them.
 *
 * A parcel has arrived once [latestArrival] has passed: the coach brings everything by then. Nothing ticks a
 * parcel forward; it is read against the clock.
 */
@Entity
@Table(
  name = "parcel",
  indexes = [Index(columnList = "recipient_master_id"), Index(columnList = "latest_arrival")]
)
class Parcel(
  @Column(name = "sender_master_id", nullable = false)
  val senderMasterId: Long,

  @Column(name = "sender_name", nullable = false)
  val senderName: String,

  @Column(name = "recipient_master_id", nullable = false)
  var recipientMasterId: Long,

  /** Settlement index of the office it left from. */
  @Column(name = "origin_office", nullable = false)
  var originOffice: Int,

  /** Settlement index of the office it goes to, the recipient's home office when it was sent. */
  @Column(name = "destination_office", nullable = false)
  var destinationOffice: Int,

  @Column(name = "weight_units", nullable = false)
  val weightUnits: Int,

  @Column(name = "freight_fee", nullable = false)
  val freightFee: Long,

  /** Never with a player courier, so it cannot be robbed. */
  @Column(name = "coach_only", nullable = false)
  val coachOnly: Boolean,

  @Column(name = "sent_at", nullable = false)
  val sentAt: Instant,

  @Column(name = "latest_arrival", nullable = false)
  var latestArrival: Instant,

  @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "container_id", nullable = false)
  val container: ItemContainer = ItemContainer(ItemContainer.Type.MAIL),
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  /**
   * On its way back to the sender, refused or never collected. A returned parcel is not returned again.
   * Not named `returning`: that is a reserved word on MariaDB, which refused the whole `create table`.
   */
  @Column(name = "is_returning", nullable = false)
  var returning: Boolean = false

  /** Auction mail: in the mailbox at once, and handed out at any post office. */
  @Column(name = "collect_anywhere", nullable = false)
  var collectAnywhere: Boolean = false

  /** Whether its recipient was told it arrived. */
  @Column(nullable = false)
  var announced: Boolean = false

  /** The sealed sack it travels in, from the route board until it arrives. */
  @Column(name = "sack_instance_id")
  var sackInstanceId: Long? = null

  /** Who carries that sack. Null while it lies on the ground after its courier fell. */
  @Column(name = "courier_master_id")
  var courierMasterId: Long? = null

  fun hasArrived(now: Instant): Boolean {
    return !now.isBefore(latestArrival)
  }

  /** A courier brought it before its latest arrival, so it has arrived now. */
  fun deliver(now: Instant) {
    latestArrival = now
    sackInstanceId = null
    courierMasterId = null
  }

  /** Waits at its origin's route board for a courier. Whatever nobody takes goes by coach. */
  fun isOnBoard(now: Instant): Boolean {
    return !coachOnly && !returning && !collectAnywhere && sackInstanceId == null &&
      originOffice != destinationOffice && !hasArrived(now)
  }
}
