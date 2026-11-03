package net.bestia.zone.honor.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.bestia.zone.honor.HonorLedger
import java.time.Instant

/**
 * What one settlement, or one townsperson in it, thinks of one Master. Rows exist only once something happened.
 *
 * Stamped with the world versions for `Rumour`'s reason: settlement indices are re-used by a new world, so a
 * surviving row would hold a grudge against a stranger in a town that never met them.
 */
@Entity
@Table(
  name = "local_honor",
  uniqueConstraints = [UniqueConstraint(columnNames = ["master_id", "scope", "holder"])],
  indexes = [Index(columnList = "master_id")],
)
class LocalHonor(
  @Id
  @Column(nullable = false)
  var id: Long = 0,

  @Column(name = "master_id", nullable = false)
  var masterId: Long = 0,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  var scope: Scope = Scope.SETTLEMENT,

  /** A settlement index for [Scope.SETTLEMENT], a `TownsfolkIdentity` for [Scope.GRUDGE]. */
  @Column(nullable = false)
  var holder: Long = 0,

  @Column(nullable = false)
  var earned: Int = 0,

  @Column(nullable = false)
  var debt: Int = 0,

  @Column(name = "last_loss_at")
  var lastLossAt: Instant? = null,

  @Column(nullable = false)
  var permanent: Boolean = false,

  /** For a grudge: the loss the witness has not yet told their town about. */
  @Column(nullable = false)
  var untold: Int = 0,

  /** The Master's name when the row last changed, for the rumour that names them once a town has had enough. */
  @Column(name = "master_name", nullable = false, length = 64)
  var masterName: String = "",

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
) {

  enum class Scope { SETTLEMENT, GRUDGE }

  /** A detached copy for the write-behind, so the tick can keep changing this row while it is saved. */
  fun copy(): LocalHonor {
    return LocalHonor(
      id, masterId, scope, holder, earned, debt, lastLossAt, permanent, untold, masterName, worldShapeVersion,
      pipelineVersion,
    )
  }

  var ledger: HonorLedger
    get() {
      return HonorLedger(earned, debt, lastLossAt, permanent)
    }
    set(value) {
      earned = value.earned
      debt = value.debt
      lastLossAt = value.lastLossAt
      permanent = value.permanent
    }
}
