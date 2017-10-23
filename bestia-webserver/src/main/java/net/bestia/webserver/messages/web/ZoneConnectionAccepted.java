package net.bestia.webserver.messages.web;

import java.util.Objects;

import org.springframework.web.socket.WebSocketSession;

import net.bestia.messages.login.LoginAuthReplyMessage;

public class ZoneConnectionAccepted implements SocketMessage {

	private final LoginAuthReplyMessage loginMessage;
	private final WebSocketSession session;
	private final String uid;

	public ZoneConnectionAccepted(LoginAuthReplyMessage loginMessage, String uid, WebSocketSession session) {

		this.loginMessage = Objects.requireNonNull(loginMessage);
		this.session = Objects.requireNonNull(session);
		this.uid = Objects.requireNonNull(uid);
	}

	public LoginAuthReplyMessage getLoginMessage() {
		return loginMessage;
	}

	public WebSocketSession getSession() {
		return session;
	}

	@Override
	public String getSessionId() {
		return uid;
	}
}
