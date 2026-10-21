package net.bestia.api.push

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** What an account wants to be woken for, and when not to. An account with no row gets [defaults]. */
@Entity
@Table(name = "push_preference")
class PushPreference(
  @Id
  @Column(name = "account_id")
  val accountId: Long,

  @Column(name = "home_fell", nullable = false, columnDefinition = "BIT(1)")
  var homeFell: Boolean = true,

  @Column(name = "bestia_died", nullable = false, columnDefinition = "BIT(1)")
  var bestiaDied: Boolean = true,

  @Column(name = "construction_done", nullable = false, columnDefinition = "BIT(1)")
  var constructionDone: Boolean = true,

  /** Minutes after midnight in [timeZone]. May wrap past midnight: 22:00 to 07:00 is 1320 to 420. */
  @Column(name = "quiet_from", nullable = true)
  var quietFrom: Short? = null,

  @Column(name = "quiet_to", nullable = true)
  var quietTo: Short? = null,

  @Column(name = "time_zone", nullable = false, length = 64)
  var timeZone: String = "UTC",

  @Column(name = "updated_at", nullable = false)
  var updatedAt: LocalDateTime = LocalDateTime.now(),
) {

  fun isQuietAt(now: ZonedDateTime): Boolean {
    val from = quietFrom?.toInt() ?: return false
    val to = quietTo?.toInt() ?: return false
    val local = now.withZoneSameInstant(zone()).toLocalTime()
    val minute = local.hour * 60 + local.minute

    return if (from <= to) minute in from until to else minute >= from || minute < to
  }

  private fun zone(): ZoneId {
    return runCatching { ZoneId.of(timeZone) }.getOrDefault(ZoneId.of("UTC"))
  }

  companion object {
    fun defaults(accountId: Long): PushPreference {
      return PushPreference(accountId)
    }

    fun minutesOf(time: LocalTime): Short {
      return (time.hour * 60 + time.minute).toShort()
    }
  }
}
