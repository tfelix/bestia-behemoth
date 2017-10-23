package net.bestia.webserver.messages.web;

import java.util.Objects;

import org.springframework.web.socket.WebSocketSession;

/**
 * Socket connection should be listed to the actor. Is used if new connections
 * are established.
 * 
 * @author Thomas Felix
 *
 */
public class PrepareConnection implements SocketMessage {

	private final String sessionUid;
	private final WebSocketSession session;

	public PrepareConnection(String sessionUid, WebSocketSession session) {

		this.sessionUid = Objects.requireNonNull(sessionUid);
		this.session = Objects.requireNonNull(session);
	}

	public WebSocketSession getSession() {
		return session;
	}

	@Override
	public String getSessionId() {
		return sessionUid;
	}
}
