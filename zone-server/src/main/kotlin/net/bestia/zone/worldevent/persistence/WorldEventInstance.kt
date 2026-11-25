package net.bestia.zone.worldevent.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** One event that started in a province: where, about what, and how it went. */
@Entity
@Table(name = "world_event_instance")
class WorldEventInstance(
  @Column(name = "event_id", nullable = false, length = 64)
  var eventId: String = "",

  @Column(name = "province_index", nullable = false)
  var provinceIndex: Int = 0,

  @Column(name = "partition_version", nullable = false)
  var partitionVersion: Long = 0,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  var state: State = State.ACTIVE,

  @Column(name = "started_at_second", nullable = false)
  var startedAtSecond: Long = 0,

  @Column(name = "ends_at_second", nullable = false)
  var endsAtSecond: Long = 0,

  @Column(nullable = false)
  var x: Long = 0,

  @Column(nullable = false)
  var y: Long = 0,

  @Column(nullable = false)
  var z: Long = 0,

  /** What the event is about: the apex bestia's entity id, a rift's row id, or null. Its event knows which. */
  @Column(name = "subject_id")
  var subjectId: Long? = null,

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  var outcome: Outcome? = null,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long = 0

  /** OMEN is for events with a warning phase, such as a rift's knot; the first events start ACTIVE. */
  enum class State { OMEN, ACTIVE, RESOLVED }

  enum class Outcome { DEFEATED, EXPIRED, CANCELLED }
}
