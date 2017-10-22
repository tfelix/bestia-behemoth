package net.bestia.webserver.messages.web;

import java.util.Objects;

import org.springframework.web.socket.WebSocketSession;

public class PrepareConnection {

	private final String sessionUid;
	private final WebSocketSession session;
	
	public PrepareConnection(String sessionUid, WebSocketSession session) {
		
		this.sessionUid = Objects.requireNonNull(sessionUid);
		this.session = Objects.requireNonNull(session);
	}
	
	public WebSocketSession getSession() {
		return session;
	}
	
	public String getSessionId() {
		return sessionUid;
	}
}
