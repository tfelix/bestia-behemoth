package net.bestia.zone.account.authentication

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.stereotype.Component

/**
 * How logins end at the zone, and what the login server's kicks do here. A refused login is only a debug line in
 * the log and a closed connection, so a wave of bad tokens (a clock that drifted, a secret that was rotated on
 * one side, someone replaying a token) looks like nothing at all without these.
 *
 * Recorded on Netty event loops and the bus thread; Micrometer's counters allow that. The default registry
 * serves tests that build the processor by hand.
 */
@Component
class AuthMetrics(registry: MeterRegistry = SimpleMeterRegistry()) {

  /** Why a login token was refused. */
  enum class Refusal {
    /** The handshake carried no authentication at all. */
    NO_PAYLOAD,

    /** Signature, expiry or shape: the token is not one login-server signed for this moment. */
    INVALID_TOKEN,

    WRONG_ISSUER,

    /** A token minted for a service other than a zone. */
    WRONG_AUDIENCE,

    /** A required claim (role, id, expiry, issue time) is missing, or the role is unknown. */
    BAD_CLAIMS,

    /** The token was presented before: a replay, or a client that retried a handshake. */
    TOKEN_REUSED,

    /** The token was issued before the account was kicked. */
    KICKED,
  }

  /** What a kick order did on this node. */
  enum class Kick {
    /** Read from the stream, whatever came of it. */
    RECEIVED,

    /** Made before this node started, so any connection here is newer. */
    PREDATES_NODE,

    /** A connection was closed. */
    CLOSED,

    /** The account has no connection here; the kick is remembered for a token on its way. */
    NO_CONNECTION,
  }

  private val accepted = Counter.builder("zone.login.accepted")
    .description("Logins whose token was accepted, before the zone checked it was ready")
    .register(registry)

  private val refused = Refusal.entries.associateWith { reason ->
    Counter.builder("zone.login.refused")
      .tag("reason", reason.name.lowercase())
      .description("Login tokens refused, by why")
      .register(registry)
  }

  private val kicks = Kick.entries.associateWith { outcome ->
    Counter.builder("zone.kicks")
      .tag("outcome", outcome.name.lowercase())
      .description("Kick orders from the login server: received, and what became of each")
      .register(registry)
  }

  fun accepted() {
    accepted.increment()
  }

  fun refused(reason: Refusal) {
    refused.getValue(reason).increment()
  }

  fun kick(outcome: Kick) {
    kicks.getValue(outcome).increment()
  }
}
