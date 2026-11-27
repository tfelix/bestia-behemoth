package net.bestia.login.admin

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The GM actions on accounts, called from the game client with its api token. Another GM action is another
 * endpoint here, behind an [net.bestia.account.Authority] of its own.
 */
@RestController
@RequestMapping("/api/v1/admin/accounts")
class AdminAccountController(
  private val gmActionService: GmActionService
) {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class KickRequest(
    val note: String
  )

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class BanRequest(
    val note: String,
    /** Null bans for good. */
    val durationHours: Long? = null
  )

  @PostMapping("/{accountId}/kick")
  fun kick(
    @PathVariable accountId: Long,
    @RequestBody request: KickRequest,
    servletRequest: HttpServletRequest
  ): ResponseEntity<Void> {
    return act(servletRequest, request.note) { actorId, note ->
      gmActionService.kick(actorId, accountId, note)
    }
  }

  @PostMapping("/{accountId}/ban")
  fun ban(
    @PathVariable accountId: Long,
    @RequestBody request: BanRequest,
    servletRequest: HttpServletRequest
  ): ResponseEntity<Void> {
    return act(servletRequest, request.note) { actorId, note ->
      gmActionService.ban(actorId, accountId, note, request.durationHours)
    }
  }

  private fun act(
    servletRequest: HttpServletRequest,
    note: String,
    action: (actorId: Long, note: String) -> Unit
  ): ResponseEntity<Void> {
    // Checked here as well as in the filter, so a path the filter does not match still fails closed.
    val actorId = servletRequest.getAttribute(ApiTokenFilter.ACCOUNT_ID) as? Long
      ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

    return try {
      action(actorId, note)
      ResponseEntity.noContent().build()
    } catch (e: GmActionRefusedException) {
      LOG.warn { "Refused a GM action by account $actorId: ${e.message}" }

      val status = when (e.refusal) {
        GmActionRefusedException.Refusal.INVALID -> HttpStatus.BAD_REQUEST
        GmActionRefusedException.Refusal.NOT_ALLOWED -> HttpStatus.FORBIDDEN
        GmActionRefusedException.Refusal.NO_SUCH_ACCOUNT -> HttpStatus.NOT_FOUND
      }
      ResponseEntity.status(status).build()
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
