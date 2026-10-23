package net.bestia.api.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * What the companion auth turns away, and why. Tags are closed sets; the account, the token and the ticket never
 * appear in one. Every combination is registered at start, so one that has not happened yet reads zero.
 */
@Component
class AuthMetrics(registry: MeterRegistry) {

  /** Why a companion token did not verify. */
  enum class TokenFailure { BAD_JWT, EXPIRED, WRONG_AUDIENCE, MISSING_SUBJECT, MISSING_EXPIRY }

  /** What the `Authorization` header offered. */
  enum class Scheme {
    BEARER,
    TICKET,
    NONE,
    ;

    companion object {
      fun of(authorization: String?): Scheme {
        return when {
          authorization == null -> NONE
          authorization.startsWith("Bearer ", ignoreCase = true) -> BEARER
          authorization.startsWith("Ticket ", ignoreCase = true) -> TICKET
          else -> NONE
        }
      }
    }
  }

  /** The status the filter answered with; [ZONE_ERROR] is a 503 or 504 from asking the zone about a ticket. */
  enum class Rejection(val tag: String) {
    UNAUTHORIZED("401"),
    RATE_LIMITED("429"),
    ZONE_ERROR("5xx"),
  }

  /** How a ticket was resolved: from memory, or by asking the zone, which knew it or did not. */
  enum class TicketLookup { CACHE_HIT, ZONE_HIT, ZONE_MISS }

  private val tokenFailures = TokenFailure.entries.associateWith {
    Counter.builder("api.auth.token.failures")
      .description("Companion tokens that did not verify")
      .tag("reason", it.name.lowercase())
      .register(registry)
  }

  private val rejected = buildMap {
    for (scheme in Scheme.entries) {
      for (rejection in Rejection.entries) {
        for (route in ApiRoute.entries) {
          put(
            Triple(scheme, rejection, route),
            Counter.builder("api.auth.rejected")
              .description("Requests the companion auth filter answered itself, by what the caller presented")
              .tag("scheme", scheme.name.lowercase())
              .tag("status", rejection.tag)
              .tag("route", route.name.lowercase())
              .register(registry),
          )
        }
      }
    }
  }

  private val rateLimited = listOf(true, false).associateWith {
    Counter.builder("api.auth.ratelimit.denied")
      .description("Calls refused because the account used up its per-minute budget")
      .tag("map", it.toString())
      .register(registry)
  }

  private val ticketLookups = TicketLookup.entries.associateWith {
    Counter.builder("api.auth.ticket.lookups")
      .description("Game-client ticket resolutions")
      .tag("outcome", it.name.lowercase())
      .register(registry)
  }

  fun tokenFailed(reason: TokenFailure) {
    tokenFailures.getValue(reason).increment()
  }

  fun rejected(scheme: Scheme, rejection: Rejection, route: ApiRoute) {
    rejected.getValue(Triple(scheme, rejection, route)).increment()
  }

  /** One denial of the per-account window; [map] says which of its two budgets ran out. */
  fun rateLimited(map: Boolean) {
    rateLimited.getValue(map).increment()
  }

  fun ticketLookup(outcome: TicketLookup) {
    ticketLookups.getValue(outcome).increment()
  }
}
