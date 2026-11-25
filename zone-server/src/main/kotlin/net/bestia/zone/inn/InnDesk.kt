package net.bestia.zone.inn

import net.bestia.zone.townsfolk.conversation.ServiceCounter
import net.bestia.zone.townsfolk.conversation.Speaker
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Component

/** An innkeeper at work opens the inn instead of a conversation, as a postmaster opens the post office. */
@Component
class InnDesk(
  private val keepers: InnKeepers,
  private val inn: InnService,
) : ServiceCounter {

  override fun serve(accountId: AccountId, speaker: Speaker): Boolean {
    val counter = keepers.counterOf(speaker) ?: return false
    inn.open(accountId, counter)

    return true
  }
}
