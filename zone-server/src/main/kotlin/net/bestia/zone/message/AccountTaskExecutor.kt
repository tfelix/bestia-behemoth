package net.bestia.zone.message

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.AccountId
import java.util.concurrent.CompletableFuture

/** Runs one account's work strictly in order, on the tick thread or on an IO thread. See [AccountInbox]. */
interface AccountTaskExecutor {
  fun onTick(accountId: AccountId, task: World.() -> Unit): CompletableFuture<Unit>

  fun onIo(accountId: AccountId, task: () -> Unit): CompletableFuture<Unit>
}
