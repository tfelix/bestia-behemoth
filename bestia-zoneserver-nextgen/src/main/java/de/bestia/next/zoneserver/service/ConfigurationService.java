package de.bestia.next.zoneserver.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConfigurationService {
	
	@Value("${server.name}")
	private String serverName;
	
	@Value("${server.port}")
	private int serverPort;
	
	public String getServerName() {
		return serverName;
	}
	
	public int getServerPort() {
		return serverPort;
	}

}
