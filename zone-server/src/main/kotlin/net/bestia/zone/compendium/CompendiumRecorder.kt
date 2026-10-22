package net.bestia.zone.compendium

import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.session.MasterPresenceChangedEvent
import net.bestia.zone.util.AccountId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Turns sightings and catches into compendium rows. Callable from any thread, the tick included: it only
 * touches the [CompendiumLedger] and queues the database work on the account's job.
 */
@Component
class CompendiumRecorder(
  private val ledger: CompendiumLedger,
  private val store: CompendiumStore,
  private val publisher: CompendiumPublisher,
  private val jobs: AsyncJobExecutor,
  private val events: ApplicationEventPublisher,
) {

  @EventListener
  fun onPresenceChanged(event: MasterPresenceChangedEvent) {
    jobs.submit(key = event.accountId) {
      if (event.online) {
        ledger.remember(event.masterId, store.load(event.masterId))
      } else {
        ledger.forget(event.masterId)
      }
    }
  }

  fun sighted(accountId: AccountId, masterId: Long, speciesId: Long) {
    if (!ledger.markSeen(masterId, speciesId)) {
      return
    }

    jobs.submit(key = accountId) {
      if (store.recordSeen(masterId, speciesId)) changed(accountId, masterId)
    }
  }

  fun caught(accountId: AccountId, masterId: Long, speciesId: Long) {
    ledger.markSeen(masterId, speciesId)

    jobs.submit(key = accountId) {
      if (store.recordCaught(masterId, speciesId)) changed(accountId, masterId)
    }
  }

  private fun changed(accountId: AccountId, masterId: Long) {
    publisher.publish(accountId, masterId)
    events.publishEvent(CompendiumChangedEvent(accountId, masterId))
  }
}
