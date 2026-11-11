package net.bestia.zone.bestia.diary

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/** Where tick code tells what an owned bestia did. A port, so behaviour trees need no Spring. */
fun interface BestiaNews {
  fun tell(happened: BestiaHappened)
}

@Component
class PublishedBestiaNews(private val events: ApplicationEventPublisher) : BestiaNews {
  override fun tell(happened: BestiaHappened) {
    events.publishEvent(happened)
  }
}
