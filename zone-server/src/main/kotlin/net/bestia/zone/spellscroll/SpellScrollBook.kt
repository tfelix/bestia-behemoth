package net.bestia.zone.spellscroll

import net.bestia.zone.spellscroll.persistence.InscribedScroll
import net.bestia.zone.spellscroll.persistence.SpellScrollInscriptionRepository
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * What every spell scroll holds, in memory, so the tick reads it without the database. The rows stay the truth:
 * this is loaded from them at boot and follows each write to them.
 */
@Component
class SpellScrollBook(
  private val repository: SpellScrollInscriptionRepository,
) {

  private val byUniqueId = ConcurrentHashMap<Long, InscribedScroll>()

  @EventListener(ApplicationReadyEvent::class)
  fun load() {
    repository.findAllInscribed().forEach(::remember)
  }

  /** What the scroll [uniqueId] holds, or null for a scroll that holds nothing or is gone. */
  fun scrollOf(uniqueId: Long): InscribedScroll? {
    return byUniqueId[uniqueId]
  }

  fun remember(scroll: InscribedScroll) {
    byUniqueId[scroll.uniqueId] = scroll
  }

  fun forget(uniqueIds: Collection<Long>) {
    uniqueIds.forEach(byUniqueId::remove)
  }
}
