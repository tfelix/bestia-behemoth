package net.bestia.zone.compendium

import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Component

/** Sends a master's whole compendium. Reads the database, so never call it on the tick thread. */
@Component
class CompendiumPublisher(
  private val store: CompendiumStore,
  private val sessions: ConnectionInfoService,
  private val outMessageProcessor: OutMessageProcessor,
) {

  /** Only while [masterId] is the master [accountId] plays; another master's book is not this one's. */
  fun publish(accountId: AccountId, masterId: Long) {
    if (sessions.activeMasterIdOf(accountId) != masterId) {
      return
    }

    outMessageProcessor.sendToPlayer(accountId, CompendiumSMSG.of(store.entries(masterId)))
  }

  fun sendTo(accountId: AccountId) {
    val masterId = sessions.activeMasterIdOf(accountId) ?: return

    publish(accountId, masterId)
  }
}
