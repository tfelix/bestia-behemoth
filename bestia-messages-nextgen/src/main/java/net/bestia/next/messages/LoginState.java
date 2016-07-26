package net.bestia.next.messages;

public enum LoginState {

	/**
	 * Login was denied.
	 */
	DENIED,

	/**
	 * Login was accepted.
	 */
	ACCEPTED,

	/**
	 * There was an internal server error while processing the request.
	 */
	SERVER_ERROR,

	/**
	 * Currenlty no logins are allowed.
	 */
	NO_LOGINS_ALLOWED

}
