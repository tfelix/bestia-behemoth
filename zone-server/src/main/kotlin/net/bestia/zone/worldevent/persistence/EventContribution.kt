package net.bestia.zone.worldevent.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** What one master did for one running world event on one axis, so it survives a restart. */
@Entity
@Table(name = "event_contribution")
class EventContribution(
  @Column(nullable = false, length = 64)
  var event: String = "",

  @Column(name = "subject_id", nullable = false)
  var subjectId: Long = 0,

  @Column(name = "master_id", nullable = false)
  var masterId: Long = 0,

  @Column(name = "master_name", nullable = false, length = 64)
  var masterName: String = "",

  @Column(nullable = false, length = 16)
  var axis: String = "",

  @Column(nullable = false)
  var points: Double = 0.0,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long = 0
}
