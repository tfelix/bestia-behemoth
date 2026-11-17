package net.bestia.zone.quest.persistence

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
import net.bestia.zone.quest.ObjectiveScope
import net.bestia.zone.quest.QuestObjective
import net.bestia.zone.quest.QuestOrigin
import net.bestia.zone.quest.QuestReward
import net.bestia.zone.quest.QuestStatus
import net.bestia.zone.quest.QuestTextArg
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/** A quest some source made at runtime. Everything it asks and pays is stored here; no definition stands behind it. */
@Entity
@Table(
  name = "quest",
  indexes = [Index(columnList = "poster_master_id"), Index(columnList = "status")]
)
class Quest(
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false)
  val origin: QuestOrigin,

  @Column(name = "title_key", nullable = false)
  val titleKey: String,

  @Convert(converter = QuestTextArgsConverter::class)
  @Column(name = "text_args", columnDefinition = "TEXT", nullable = false)
  val textArgs: List<QuestTextArg>,

  @Column(name = "suggested_level", nullable = false)
  val suggestedLevel: Int,

  @Column(nullable = false)
  val deadline: Instant,

  @Convert(converter = QuestObjectivesConverter::class)
  @Column(columnDefinition = "TEXT", nullable = false)
  var objectives: List<QuestObjective>,

  @Convert(converter = QuestRewardsConverter::class)
  @Column(columnDefinition = "TEXT", nullable = false)
  val rewards: List<QuestReward>,

  /** Null for a quest nobody posted, such as one the world made. */
  @Column(name = "poster_master_id")
  val posterMasterId: Long?,

  /** A snapshot, so a deleted master still has a name on the quest. */
  @Column(name = "poster_name")
  val posterName: String?,

  @Column(name = "created_at", nullable = false)
  val createdAt: Instant,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false)
  var status: QuestStatus = QuestStatus.OPEN

  @Column(name = "ended_at")
  var endedAt: Instant? = null

  /** The poster cleared the ended quest from its own log. Accepting masters clear theirs by dropping the entry. */
  @Column(name = "poster_cleared", nullable = false)
  var posterCleared: Boolean = false

  val sharedObjectivesDone: Boolean
    get() = objectives.filter { it.scope == ObjectiveScope.SHARED }.all { it.remaining <= 0 }

  fun close() {
    check(status == QuestStatus.OPEN) { "Quest $id is $status, it cannot start closing" }
    status = QuestStatus.CLOSING
  }

  fun end(endStatus: QuestStatus, at: Instant) {
    require(endStatus.isEnded) { "$endStatus does not end a quest" }
    check(!status.isEnded) { "Quest $id has already ended as $status" }
    status = endStatus
    endedAt = at
  }
}
