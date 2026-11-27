package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.LoginClient
import net.bestia.bus.contract.LoginCommand
import net.bestia.bus.contract.LoginReply.Outcome
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The GM actions of the companion app. Any signed-in account may call them: the login server owns the roles and
 * checks the caller's authority on every action, so a GM who was demoted or banned a minute ago is refused (403).
 *
 * A 504 means the login server did not answer in time and the action may or may not have happened. Kicks and bans
 * may be sent again.
 */
@RestController
@RequestMapping("/v1/companion/gm/accounts/{accountId}")
class GmController(
  private val login: LoginClient,
) {

  data class AccountKick(val note: String)

  /** A null [durationHours] bans for good. */
  data class AccountBan(val note: String, val durationHours: Long? = null)

  @PostMapping("/kick")
  fun kick(
    @RequestAttribute(ACCOUNT_ID) actorAccountId: Long,
    @PathVariable accountId: Long,
    @RequestBody kick: AccountKick,
  ): ResponseEntity<Void> {
    return answer(login.send(actorAccountId, LoginCommand.Kick(accountId, kick.note)))
  }

  @PostMapping("/ban")
  fun ban(
    @RequestAttribute(ACCOUNT_ID) actorAccountId: Long,
    @PathVariable accountId: Long,
    @RequestBody ban: AccountBan,
  ): ResponseEntity<Void> {
    return answer(login.send(actorAccountId, LoginCommand.Ban(accountId, ban.note, ban.durationHours)))
  }

  private fun answer(outcome: Outcome): ResponseEntity<Void> {
    val status = when (outcome) {
      Outcome.DONE -> HttpStatus.NO_CONTENT
      Outcome.INVALID -> HttpStatus.BAD_REQUEST
      Outcome.NOT_ALLOWED -> HttpStatus.FORBIDDEN
      Outcome.NO_SUCH_ACCOUNT -> HttpStatus.NOT_FOUND
      Outcome.FAILED -> HttpStatus.BAD_GATEWAY
    }

    return ResponseEntity.status(status).build()
  }
}
