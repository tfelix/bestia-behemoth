package net.bestia.messages.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This message is send towards actors (usually an IngestActor) which will then
 * redirect all messages towards the actor.
 * 
 * @author Thomas Felix
 *
 */
public class RedirectRequestMessage {

	private List<Class<? extends Object>> classes = new ArrayList<>();

	private RedirectRequestMessage() {
		// no op
	}

	@SafeVarargs
	public static RedirectRequestMessage get(Class<? extends Object>... classes) {
		RedirectRequestMessage req = new RedirectRequestMessage();
		req.classes.addAll(Arrays.asList(classes));
		return req;
	}

	/**
	 * Returns the list of classes of messages which should be redirected
	 * towards the requesting actor.
	 * 
	 * @return A list of classes.
	 */
	public List<Class<? extends Object>> getClasses() {
		return classes;
	}
}