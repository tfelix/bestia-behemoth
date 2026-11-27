package net.bestia.bus

import net.bestia.bus.contract.LoginCommand
import net.bestia.bus.contract.LoginReply
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * The api-server's way to have the login server act on an account.
 *
 * The future fails with [LoginUnavailableException] when no login server is listening, and with a
 * [java.util.concurrent.TimeoutException] when none answered in time. A command that timed out may or may not have
 * happened; kicks and bans may be sent again.
 */
interface LoginBus {

  fun send(
    actorAccountId: Long,
    command: LoginCommand,
    timeout: Duration = DEFAULT_TIMEOUT,
  ): CompletableFuture<LoginReply.Outcome>

  companion object {
    val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(5)
  }
}
