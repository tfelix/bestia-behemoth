package net.bestia.webserver.controller;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.bestia.model.server.MaintenanceLevel;
import net.bestia.webserver.actor.WebserverActorApi;
import net.bestia.webserver.service.ConfigurationService;

/**
 * Controller provides REST information about the server.
 * 
 * @author Thomas Felix
 *
 */
@RestController
@RequestMapping("v1/server")
public class ServerController {

	private final ConfigurationService config;
	private final WebserverActorApi akkaApi;

	private static class ServerStatus {
		public final MaintenanceLevel maintenance;
		public final String motd;

		public ServerStatus(MaintenanceLevel maintenance, String motd) {

			this.maintenance = maintenance;
			this.motd = motd;
		}
	}

	@Autowired
	public ServerController(ConfigurationService config,
			WebserverActorApi akkaApi) {

		this.config = Objects.requireNonNull(config);
		this.akkaApi = Objects.requireNonNull(akkaApi);
	}

	@CrossOrigin(origins = "http://localhost")
	@RequestMapping(value = "status")
	public ResponseEntity<ServerStatus> status() {

		// TODO Noch implementieren.
		
		final ServerStatus status = new ServerStatus(MaintenanceLevel.PARTIAL, "Hello World");
		
		return new ResponseEntity<>(status, HttpStatus.OK);
	}
}
