package net.bestia.zone.chronicle.persistence

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.EventKind

/**
 * Something that happened in play and belongs to the history of the world. The generated chronicle ends when
 * play starts and is never stored; these rows are what came after. A row is never changed.
 */
@Entity
@Table(name = "world_fact", indexes = [Index(columnList = "subject_type, subject_index")])
class WorldFact(
  /** In the chronicle's own words, so a fact reads like the events before it. */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 48)
  val kind: EventKind,

  /** Everyone and everything involved, the subject first, as in a `HistoryEvent`. */
  @Convert(converter = ActorsConverter::class)
  @Column(nullable = false, length = 255)
  val actors: List<Actor>,

  /** World metres, when the place means something. */
  @Column(name = "where_x")
  val whereX: Double?,

  @Column(name = "where_y")
  val whereY: Double?,

  /** The master who did it. No foreign key: the history keeps what a deleted master did. */
  @Column(name = "by_master_id")
  val byMasterId: Long?,

  /** A snapshot, so the deed keeps its name. */
  @Column(name = "by_master_name")
  val byMasterName: String?,

  /** The game day it happened on, counted from the start of play. */
  @Column(name = "on_day", nullable = false)
  val onDay: Double,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  /** The first actor again, in columns of their own: "what happened to this artifact" is the common question. */
  @Enumerated(EnumType.STRING)
  @Column(name = "subject_type", nullable = false, length = 16)
  val subjectType: ActorType = actors.first().type

  @Column(name = "subject_index", nullable = false)
  val subjectIndex: Int = actors.first().index
}
