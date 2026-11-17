package net.bestia.zone.quest.persistence

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * A quest a master accepted. The entries of a quest are who works on it, so no source keeps its own list.
 * [counts] holds this master's count per objective, in the quest's objective order.
 *
 * [masterId] is no foreign key: masters live in a slice above this one.
 */
@Entity
@Table(
  name = "quest_log_entry",
  uniqueConstraints = [UniqueConstraint(columnNames = ["master_id", "quest_id"])],
  indexes = [Index(columnList = "master_id")]
)
class QuestLogEntry(
  @Column(name = "master_id", nullable = false)
  val masterId: Long,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "quest_id", nullable = false)
  val quest: Quest,

  @Column(name = "accepted_at", nullable = false)
  val acceptedAt: Instant,

  @Convert(converter = CountsConverter::class)
  @Column(columnDefinition = "TEXT", nullable = false)
  var counts: List<Int>,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
