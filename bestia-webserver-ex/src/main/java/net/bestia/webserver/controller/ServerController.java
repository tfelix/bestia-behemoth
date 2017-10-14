package net.bestia.webserver.controller;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.bestia.messages.account.ServerStatusMessage;
import net.bestia.webserver.actor.WebserverActorApi;

/**
 * Controller provides REST information about the server.
 * 
 * @author Thomas Felix
 *
 */
@RestController
@RequestMapping("v1/server")
public class ServerController {

	private final WebserverActorApi akkaApi = null;

	//@Autowired
	public ServerController() {

		//this.akkaApi = Objects.requireNonNull(akkaApi);
	}

	/**
	 * Show the current login status of the server and the MOTD.
	 * 
	 * @return Current server status.
	 */
	@CrossOrigin(origins = "http://localhost")
	@RequestMapping(value = "status")
	public ResponseEntity<ServerStatusMessage> status() {

		final ServerStatusMessage statusReply = akkaApi.requestServerStatus();
		return new ResponseEntity<>(statusReply, HttpStatus.OK);
	}
}
