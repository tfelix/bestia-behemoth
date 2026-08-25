package net.bestia.login.webauthn

/**
 * Anything that stops a ceremony from completing. The message is for the log; what reaches the
 * browser is a fixed string, because distinguishing "no such credential" from "signature did not
 * verify" tells an attacker which of the two they got wrong.
 */
open class WebAuthnException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The one deliberate exception to that rule: a discoverable credential the authenticator still
 * holds, but this server has no row for (most commonly a wiped dev database). Credential ids are
 * authenticator-chosen and high-entropy, not user-guessable, so naming this case specifically costs
 * little and saves the player from retrying a passkey that can never work here again.
 */
class UnknownCredentialException : WebAuthnException("Credential not registered on this server")
