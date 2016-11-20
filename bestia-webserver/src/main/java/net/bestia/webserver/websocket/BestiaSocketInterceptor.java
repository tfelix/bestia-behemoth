package net.bestia.webserver.websocket;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * This interceptor will deny all connection requests from clients as long as
 * there is no connection to the bestia zone server cluster. As soon as there is
 * a connection the interceptor will allow new connections.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaSocketInterceptor implements HandshakeInterceptor {

	private AtomicBoolean allowConnections = new AtomicBoolean(true);

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler waHandler,
			Exception exception) {
		// no op.
	}

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Map<String, Object> attrib) throws Exception {
		return allowConnections.get();
	}

	/**
	 * Sets the flag if new incoming connections to this server are allowed or
	 * not.
	 * 
	 * @param flag
	 */
	public void allowConnections(boolean flag) {
		allowConnections.set(flag);
	}
}
