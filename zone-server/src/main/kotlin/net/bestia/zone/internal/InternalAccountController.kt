package net.bestia.zone.internal

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import net.bestia.account.KickReason
import net.bestia.internal.ServiceTokens
import net.bestia.zone.account.authentication.KickedAccounts
import net.bestia.zone.socket.ConnectionTerminator
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** What the login server may do to an account on this zone. */
@RestController
class InternalAccountController(
  private val connections: ConnectionTerminator,
  private val kickedAccounts: KickedAccounts
) {

  data class KickRequest(
    val reason: KickReason
  )

  /** Answers the same whether or not the account plays here: the login server asks every zone it might be on. */
  @PostMapping(ServiceTokens.KICK_PATH)
  fun kick(
    @PathVariable accountId: Long,
    @RequestBody request: KickRequest,
    servletRequest: HttpServletRequest
  ): ResponseEntity<Void> {
    // Checked here as well as in the filter, so a path the filter does not match still fails closed.
    val call = servletRequest.getAttribute(InternalApiFilter.SERVICE_CALL) as? ServiceTokenValidator.ServiceCall
      ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

    if (call.accountId != accountId || call.scope != ServiceTokens.KICK_SCOPE) {
      LOG.warn { "Refused a kick of account $accountId by a token for ${call.scope} on account ${call.accountId}" }
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
    }

    // Remembered even when the account is not here: it may be on its way, with a token issued before the kick.
    kickedAccounts.remember(accountId)
    connections.disconnect(accountId, request.reason.name)

    return ResponseEntity.noContent().build()
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
