package net.bestia.zone.auction.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * A Trade Agreement between two auction house owners: every house of each shows the listings of every house of
 * the other. Stored with the lower master id first, so a pair has one row.
 */
@Entity
@Table(name = "trade_link")
class TradeLink(
  @Column(name = "first_master_id", nullable = false)
  val firstMasterId: Long,

  @Column(name = "first_name", nullable = false)
  val firstName: String,

  @Column(name = "second_master_id", nullable = false)
  val secondMasterId: Long,

  @Column(name = "second_name", nullable = false)
  val secondName: String,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
