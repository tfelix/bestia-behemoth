package net.bestia.webserver.controller;

import java.util.Objects;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.bestia.messages.web.AccountLoginToken;
import net.bestia.webserver.actor.WebserverLogin;

/**
 * This controller provides a rest interface to control logins of the clients.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@RestController("account/")
public class AccountController {
	
	private WebserverLogin login;
	
	@Autowired
	public AccountController(WebserverLogin login) {
		
		this.login = Objects.requireNonNull(login);
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
	@RequestMapping("login")
	public AccountLoginToken login(
			@RequestParam(value = "accName") String account,
			@RequestParam(value = "password") String password, HttpServletResponse response) {

		return login.getLoginToken(account, password);
	}
}
