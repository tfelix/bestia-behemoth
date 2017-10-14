package net.bestia.webserver.actor;

import org.springframework.web.socket.WebSocketSession;

import net.bestia.messages.account.AccountLoginRequest;
import net.bestia.messages.account.AccountRegistration;
import net.bestia.messages.account.AccountRegistrationReply;
import net.bestia.messages.account.ServerStatusMessage;
import net.bestia.messages.account.UserNameCheck;
import net.bestia.webserver.exceptions.WrongCredentialsException;

public interface WebserverActorApi {

	UserNameCheck checkAvailableUserName(UserNameCheck data);

	/**
	 * Generates a new login token for a given account and password combination.
	 * 
	 * @param accName
	 *            The user/account name.
	 * @param password
	 *            The password of this account.
	 * @return The newly generated {@link AccountLoginToken} containing a valid
	 *         login token.
	 * @throws WrongCredentialsException
	 *             If the provided accName or password were not found or did not
	 *             match.
	 */
	AccountLoginRequest getLoginToken(String accName, String password) throws WrongCredentialsException;

	/**
	 * Sets a new password for the given user account.
	 * 
	 * @param accName
	 *            The name of the account. Usually this contains the email
	 *            address.
	 * @param oldPassword
	 *            The old password must be given for validation.
	 * @param newPassword
	 *            The new password.
	 * @return TRUE of the password setting was successful.
	 * @throws WrongCredentialsException
	 *             If the old password was not correct.
	 */
	boolean setPassword(String accName, String oldPassword, String newPassword) throws WrongCredentialsException;

	/**
	 * Creates a new websocket connection so it can exchange messages with the
	 * bestia backend.
	 * 
	 * @param sessionUid
	 *            A unique id naming the connection.
	 * @param session
	 *            The websocket session.
	 */
	void setupWebsocketConnection(String sessionUid, WebSocketSession session);

	/**
	 * This closes the websocket session.
	 * 
	 * @param sessionUid
	 *            The websocket session UID to be closed.
	 */
	void closeWebsocketConnection(String sessionUid);

	/**
	 * The message from the client must be handled towards the bestia cluster
	 * server.
	 * 
	 * @param sessionUid
	 *            The uid of the session from this message.
	 * @param payload
	 *            The message payload.
	 */
	void handleClientMessage(String sessionUid, String payload);

	/**
	 * Requests the current status of the server.
	 * 
	 * @return The current server status of NULL if the request could not be
	 *         processed.
	 */
	ServerStatusMessage requestServerStatus();

	/**
	 * Tries to register a new account into the bestia system.
	 * 
	 * @param registration
	 *            The data needed for account registration.
	 * @return The status of the registration.
	 */
	AccountRegistrationReply registerAccount(AccountRegistration registration);
}
