package net.bestia.zone.spawn.ecs

import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Component

/**
 * Whose turn the next drop is in each party that takes turns. Tick thread only. Forgotten on a restart, which
 * only starts each round over.
 */
@Component
class LootTurns {

  private val lastTakerByParty = HashMap<Long, AccountId>()

  /** The first of [candidates] after the party's last taker, in account order, starting over after the last. */
  fun next(partyId: Long, candidates: Collection<AccountId>): AccountId {
    val inOrder = candidates.sorted()
    val lastTaker = lastTakerByParty[partyId]
    val taker = inOrder.firstOrNull { lastTaker == null || it > lastTaker } ?: inOrder.first()
    lastTakerByParty[partyId] = taker

    return taker
  }
}
