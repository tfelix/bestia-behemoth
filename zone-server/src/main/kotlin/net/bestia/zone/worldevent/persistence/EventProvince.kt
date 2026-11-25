package net.bestia.zone.worldevent.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * One province's room for events: a budget that grows a little every Bestia day and pays for each event, and the
 * quiet after one. The partition version drops the row when the provinces were split differently.
 */
@Entity
@Table(name = "event_province")
class EventProvince(
  @Id
  @Column(name = "province_index", nullable = false)
  var provinceIndex: Int = 0,

  @Column(name = "partition_version", nullable = false)
  var partitionVersion: Long = 0,

  @Column(nullable = false)
  var budget: Double = 0.0,

  /** The Bestia second the budget last grew at. */
  @Column(name = "accrued_at_second", nullable = false)
  var accruedAtSecond: Long = 0,

  /** No new event starts here before this Bestia second. */
  @Column(name = "quiet_until_second", nullable = false)
  var quietUntilSecond: Long = 0,
)
