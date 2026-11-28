package net.bestia.zone.config

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Whether every boot runner has finished. Scheduled jobs start before the runners do, so a job that reads a
 * catalogue the importers fill must wait for this, or it caches a half-imported catalogue for good.
 */
@Component
class BootCompletion {

  @Volatile
  private var complete = false

  val isComplete: Boolean
    get() {
      return complete
    }

  @EventListener(ApplicationReadyEvent::class)
  fun onReady() {
    complete = true
  }
}
