package net.bestia.login.admin

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bus.LoginCommandHandler
import net.bestia.bus.contract.LoginCommand
import net.bestia.bus.contract.LoginReply.Outcome
import net.bestia.login.admin.GmActionRefusedException.Refusal
import org.springframework.stereotype.Component

/** Carries out the GM actions the api-server sends over the bus. */
@Component
class GmCommandHandler(
  private val gmActionService: GmActionService
) : LoginCommandHandler {

  override fun handle(actorAccountId: Long, command: LoginCommand): Outcome {
    try {
      when (command) {
        is LoginCommand.Kick -> gmActionService.kick(actorAccountId, command.targetAccountId, command.note)
        is LoginCommand.Ban -> {
          gmActionService.ban(actorAccountId, command.targetAccountId, command.note, command.durationHours)
        }
      }
      return Outcome.DONE
    } catch (e: GmActionRefusedException) {
      LOG.warn { "Refused a GM action by account $actorAccountId: ${e.message}" }

      return when (e.refusal) {
        Refusal.INVALID -> Outcome.INVALID
        Refusal.NOT_ALLOWED -> Outcome.NOT_ALLOWED
        Refusal.NO_SUCH_ACCOUNT -> Outcome.NO_SUCH_ACCOUNT
      }
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
