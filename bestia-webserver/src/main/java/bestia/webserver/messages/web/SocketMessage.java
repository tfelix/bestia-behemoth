package bestia.webserver.messages.web;

/**
 * Messages directed to a certain socket with a socket/session id.
 * 
 * @author Thomas Felix
 *
 */
public interface SocketMessage {

	/**
	 * Session id of the receiving socket.
	 * 
	 * @return
	 */
	String getSessionId();
}
