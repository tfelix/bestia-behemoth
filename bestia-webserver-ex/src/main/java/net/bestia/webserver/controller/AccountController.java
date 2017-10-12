package net.bestia.webserver.controller;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.bestia.messages.web.AccountLoginRequest;
import net.bestia.messages.web.AccountRegistration;
import net.bestia.messages.web.AccountRegistrationReply;
import net.bestia.messages.web.UserNameCheck;
import net.bestia.webserver.actor.WebserverActorApi;
import net.bestia.webserver.exceptions.NoConnectedException;
import net.bestia.webserver.service.ConfigurationService;

/**
 * This controller provides a rest interface to control logins of the clients.
 * 
 * @author Thomas Felix
 *
 */
@RestController
@RequestMapping("v1/account")
public class AccountController {

	private final ConfigurationService config;
	private final WebserverActorApi akkaApi = null;

	/**
	 * Transforms the server reply to a better usable response for the clients.
	 * 
	 */
	@SuppressWarnings("unused")
	private final class TokenResponse {

		public final boolean success;
		public final String username;
		public final long accountId;
		public final String token;

		public TokenResponse(AccountLoginRequest req) {

			if (req.getAccountId() == 0) {
				this.success = false;
				this.username = "";
				this.accountId = 0;
				this.token = "";
			} else {
				this.success = true;
				this.username = req.getUsername();
				this.accountId = req.getAccountId();
				this.token = req.getToken();
			}

		}
	}

	@Autowired
	public AccountController(ConfigurationService config) {

		this.config = Objects.requireNonNull(config);
		//this.akkaApi = Objects.requireNonNull(akkaApi);
	}

	/**
	 * Performs a login of the client. Asks the bestia server system if the
	 * login credentials are correct and provides the account with a new token.
	 * Otherwise an error is reported.
	 * 
	 * @param account
	 *            The name of the account to login.
	 * @param password
	 *            The password to this account.
	 */
	@CrossOrigin(origins = "http://localhost")
	@RequestMapping("login")
	public ResponseEntity<TokenResponse> login(
			@RequestParam(value = "accName") String account,
			@RequestParam(value = "password") String password) {

		NoConnectedException.isConnectedOrThrow(config);

		final AccountLoginRequest request = akkaApi.getLoginToken(account, password);
		final TokenResponse response = new TokenResponse(request);

		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	/**
	 * Resets the password to a new one.
	 * 
	 * @param oldPassword
	 *            The old password.
	 * @param newPassword
	 *            The new password.
	 * @param email
	 *            Name or E-Mail of the account to reset the password.
	 */
	@CrossOrigin(origins = "http://localhost")
	@RequestMapping("password")
	public ResponseEntity<String> password(
			@RequestParam(value = "oldPassword") String oldPassword,
			@RequestParam(value = "newPassword") String newPassword,
			@RequestParam(value = "email") String email) {
		NoConnectedException.isConnectedOrThrow(config);

		final boolean wasSuccessful = akkaApi.setPassword(email, oldPassword, newPassword);

		if (wasSuccessful) {
			return new ResponseEntity<>(HttpStatus.OK);
		} else {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
	}

	/**
	 * Registers a new account with the bestia server.
	 */
	@CrossOrigin(origins = "http://localhost")
	@RequestMapping(value = "register", method = RequestMethod.POST)
	public ResponseEntity<AccountRegistrationReply> register(@RequestBody AccountRegistration registration) {

		final AccountRegistrationReply reply = akkaApi.registerAccount(registration);
		return new ResponseEntity<>(reply, HttpStatus.OK);
	}

	/**
	 * Checks the entered user data if this is valid (username not taken etc.)
	 */
	@CrossOrigin(origins = "http://localhost")
	@RequestMapping("check")
	public ResponseEntity<UserNameCheck> checkData(
			@RequestParam(value = "username") String username,
			@RequestParam(value = "email") String email) {

		final UserNameCheck usernameCheck = new UserNameCheck(username, email);
		final UserNameCheck checkedUsername = akkaApi.checkAvailableUserName(usernameCheck);

		if (checkedUsername == null) {
			return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
		} else {
			return new ResponseEntity<>(checkedUsername, HttpStatus.OK);
		}
	}
}
