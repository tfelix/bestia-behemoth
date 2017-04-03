package net.bestia.webserver.exceptions;

import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

/**
 * Exception can be thrown when the wrong user credentials where selected.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class WrongCredentialsException extends RuntimeException {

	private static final long serialVersionUID = 1L;

}
