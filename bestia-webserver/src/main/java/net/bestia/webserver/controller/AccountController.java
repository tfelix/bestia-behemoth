package net.bestia.webserver.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {
	
	//@RequestParam(value="name", defaultValue="World") String name

	@RequestMapping("/login")
    public String login() {
        return "Hello World";
    }
}
