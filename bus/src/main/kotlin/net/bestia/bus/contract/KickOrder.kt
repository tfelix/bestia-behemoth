package net.bestia.bus.contract

/**
 * The login server ended an account's sessions - a ban, a GM kick, an account recovery - and every zone node
 * must close its connection and refuse its login tokens issued before [kickedAtEpochMs].
 *
 * [reason] is a `KickReason` name; the bus does not depend on `shared`.
 */
data class KickOrder(val accountId: Long, val reason: String, val kickedAtEpochMs: Long)
