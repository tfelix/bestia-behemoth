package net.bestia.zone.message

import net.bestia.zone.util.AccountId
import java.util.concurrent.CompletableFuture

/** Runs one account's work strictly in order, each task on its [HandlerLane]. See [AccountInbox]. */
fun interface AccountTaskExecutor {
  fun execute(accountId: AccountId, lane: HandlerLane, task: () -> Unit): CompletableFuture<Unit>
}
