package net.bestia.login.webauthn

/**
 * Anything that stops a ceremony from completing. The message is for the log; what reaches the
 * browser is a fixed string, because distinguishing "no such credential" from "signature did not
 * verify" tells an attacker which of the two they got wrong.
 */
class WebAuthnException(message: String, cause: Throwable? = null) : Exception(message, cause)
