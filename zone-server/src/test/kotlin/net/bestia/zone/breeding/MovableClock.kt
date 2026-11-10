package net.bestia.zone.breeding

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock the breeding tests move forward by hand, as the timers read it. */
class MovableClock(private var now: Instant) : Clock() {
  fun advance(by: Duration) {
    now = now.plus(by)
  }

  override fun instant(): Instant = now
  override fun getZone(): ZoneId = ZoneOffset.UTC
  override fun withZone(zone: ZoneId?): Clock = this
}
