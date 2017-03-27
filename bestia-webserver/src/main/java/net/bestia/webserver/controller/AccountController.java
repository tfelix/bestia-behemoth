package net.bestia.webserver.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.bestia.messages.web.AccountLoginToken;
import net.bestia.webserver.exceptions.WrongCredentialsException;

/**
 * This controller provides a rest interface to control logins of the clients.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@RestController("account/")
public class AccountController {


	@RequestMapping("login")
	public AccountLoginToken login(
			@RequestParam(value = "accName") String account,
			@RequestParam(value = "password") String password) {

		throw new WrongCredentialsException();

		/*
		 * final AccountLoginToken creds = new AccountLoginToken(acc.getId(),
		 * acc.getLoginToken(), acc.getMaster().getName()); return creds;
		 */
	}
}
