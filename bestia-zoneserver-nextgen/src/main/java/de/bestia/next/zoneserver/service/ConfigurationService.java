package de.bestia.next.zoneserver.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConfigurationService {
	
	@Value("${server.name}")
	private String serverName;
	
	public String getServerName() {
		return serverName;
	}

}
