package net.bestia.webserver.controller;

import java.util.Objects;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.bestia.messages.web.AccountLoginToken;
import net.bestia.model.web.AccountRegistration;
import net.bestia.model.web.UserNameCheck;
import net.bestia.webserver.actor.WebserverActorApi;
import net.bestia.webserver.exceptions.NoConnectedException;
import net.bestia.webserver.service.ConfigurationService;

/**
 * This controller provides a rest interface to control logins of the clients.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@RestController
@RequestMapping("v1/account")
public class AccountController {

	private final WebserverActorApi login;
	private final ConfigurationService config;

	@Autowired
	public AccountController(WebserverActorApi login, ConfigurationService config) {

		this.login = Objects.requireNonNull(login);
		this.config = Objects.requireNonNull(config);
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
	 * @return
	 */
	@CrossOrigin(origins = "http://localhost")
	@RequestMapping("login")
	public AccountLoginToken login(
			@RequestParam(value = "accName") String account,
			@RequestParam(value = "password") String password, HttpServletResponse response) {
		NoConnectedException.isConnectedOrThrow(config);
		return login.getLoginToken(account, password);
	}

	/**
	 * Resets the password to a new one.
	 * 
	 * @param account
	 * @param password
	 * @param email
	 * @return
	 */
	@CrossOrigin(origins = "http://localhost")
	@RequestMapping("password")
	public AccountLoginToken password(
			@RequestParam(value = "oldPassword") String account,
			@RequestParam(value = "newPassword") String password,
			@RequestParam(value = "email") String email) {
		NoConnectedException.isConnectedOrThrow(config);

		throw new IllegalStateException("Not implemented.");
		// return login.getLoginToken(account, password);
	}

	/**
	 * Registers a new account with the bestia server.
	 */
	@CrossOrigin(origins = "http://localhost")
	@RequestMapping(value = "register", method = RequestMethod.POST)
	public ResponseEntity register(@RequestBody AccountRegistration registration) {

		return new ResponseEntity<>(HttpStatus.OK);
	}

	/**
	 * Checks the entered user data if this is valid (username not taken etc.)
	 */
	@CrossOrigin(origins = "http://localhost")
	@RequestMapping("check")
	public ResponseEntity<UserNameCheck> checkData(@RequestBody UserNameCheck check) {
		
		
		return null;
	}
}
