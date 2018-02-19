package bestia.webserver.messages.web;

import java.util.Objects;

public class CloseConnection implements SocketMessage {
	
	private final String uid;
	
	public CloseConnection(String uid) {
		
		this.uid = Objects.requireNonNull(uid);
	}

	@Override
	public String getSessionId() {
		return uid;
	}
}
