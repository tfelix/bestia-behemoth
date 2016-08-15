package net.bestia.messages.system;

import java.util.Objects;

import net.bestia.messages.SystemMessage;

public class LoadMapfileMessage extends SystemMessage {

	private static final long serialVersionUID = 1L;
	
	private final String mapfile;
	
	public LoadMapfileMessage(String mapfile) {
		
		this.mapfile = Objects.requireNonNull(mapfile);
	}
	
	public String getMapfile() {
		return mapfile;
	}
}
