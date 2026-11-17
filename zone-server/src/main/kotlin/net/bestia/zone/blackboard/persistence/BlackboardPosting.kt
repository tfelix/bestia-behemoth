package net.bestia.zone.blackboard.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * The money behind one blackboard quest. The pay was taken from the poster's bag when it was posted and is held
 * here as a number, like an auction bid; deliverers are paid from [payLeft], and what is left goes back by post.
 */
@Entity
@Table(
  name = "blackboard_posting",
  uniqueConstraints = [UniqueConstraint(columnNames = ["quest_id"])],
  indexes = [Index(columnList = "poster_master_id")]
)
class BlackboardPosting(
  @Column(name = "quest_id", nullable = false)
  val questId: Long,

  @Column(name = "poster_master_id", nullable = false)
  val posterMasterId: Long,

  @Column(name = "coins_each", nullable = false)
  val coinsEach: Long,

  @Column(name = "pay_left", nullable = false)
  var payLeft: Long,

  @Column(name = "fee_paid", nullable = false)
  val feePaid: Long,

  @Column(nullable = false)
  val deadline: Instant,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  /** When a withdrawn quest stops taking hand-ins. */
  @Column(name = "closes_at")
  var closesAt: Instant? = null

  /** Ended and paid out: nothing more moves through this posting. */
  @Column(nullable = false)
  var settled: Boolean = false
}
