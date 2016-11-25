package net.bestia.webserver.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController("account/")
public class AccountController {

	@RequestMapping("login")
	public String login(@RequestParam(value = "accName") String account,
			@RequestParam(value = "password") String password) {

		return "Hello World";
	}
}
