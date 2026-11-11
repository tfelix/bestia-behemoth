package net.bestia.zone.bestia.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import net.bestia.zone.bestia.diary.BestiaHappened
import java.time.Instant

/** One line of a bestia's diary. Kept for [DiaryConfig.keepDays]. */
@Entity
@Table(
  name = "bestia_diary_line",
  indexes = [Index(columnList = "master_id, at"), Index(columnList = "player_bestia_id, at")],
)
class DiaryLine(
  @Column(name = "master_id", nullable = false)
  val masterId: Long,

  @Column(name = "player_bestia_id", nullable = false)
  val playerBestiaId: Long,

  @Column(nullable = false)
  val at: Instant,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  val kind: BestiaHappened.Kind,

  /** `identifier:amount` pairs joined by `,`. */
  @Column(length = 400)
  val items: String? = null,

  @Column(length = 40)
  val place: String? = null,

  @Column(length = 40)
  val reason: String? = null,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  fun itemAmounts(): Map<String, Int> {
    return items.orEmpty().split(',').filter { it.isNotBlank() }.associate { pair ->
      val (identifier, amount) = pair.split(':')
      identifier to amount.toInt()
    }
  }

  companion object {
    fun itemsOf(items: Map<String, Int>): String? {
      return items.takeIf { it.isNotEmpty() }?.entries?.joinToString(",") { (identifier, amount) -> "$identifier:$amount" }
    }
  }
}
