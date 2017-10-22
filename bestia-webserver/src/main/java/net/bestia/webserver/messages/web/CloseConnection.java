package net.bestia.webserver.messages.web;

import java.util.Objects;

public class CloseConnection {
	
	private final String uid;
	
	public CloseConnection(String uid) {
		
		this.uid = Objects.requireNonNull(uid);
	}
	
	public String getUid() {
		return uid;
	}
}
