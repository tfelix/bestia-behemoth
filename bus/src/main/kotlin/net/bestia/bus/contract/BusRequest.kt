package net.bestia.bus.contract

/**
 * One question on its way to a zone.
 *
 * [accountId] is the caller as the api-service authenticated it. The zone trusts it because only the api-service
 * can publish to a zone's request channel (Valkey ACL), and checks every master in [query] against it.
 *
 * @property replyTo the channel of the api-service instance that is waiting
 * @property deadlineEpochMs past this the caller has given up, so the zone does not start the work
 * @property idempotencyKey set on writes; a repeat within the key's lifetime gets the first reply back
 */
data class BusRequest(
  val correlationId: String,
  val replyTo: String,
  val deadlineEpochMs: Long,
  val accountId: Long,
  val idempotencyKey: String?,
  val query: ZoneQuery,
)
