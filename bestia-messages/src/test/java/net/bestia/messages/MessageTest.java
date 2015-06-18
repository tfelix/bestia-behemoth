package net.bestia.messages;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.reflections.Reflections;

public class MessageTest {

	private static Reflections reflections = new Reflections("net.bestia.messages");

	/**
	 * Look if every message class has an std. ctor and can be instantiated. Important for jackson serialization.
	 */
	@Test
	public void std_ctor_present_test() {

		Set<Class<? extends Message>> subTypes = reflections.getSubTypesOf(Message.class);

		List<String> notInstances = new ArrayList<>();

		for (Class<? extends Message> clazz : subTypes) {
			try {
				clazz.newInstance();
			} catch (Exception ex) {
				notInstances.add(clazz.getName());
			}
		}

		String msg = "Could not instanciated: " + notInstances.toString();

		Assert.assertTrue(msg, notInstances.isEmpty());
	}

	@Test
	public void duplicate_msg_id_test() throws Exception {

		List<String> duplicates = new LinkedList<>();
		Set<String> ids = new HashSet<>();

		// Find all classes implementing the message interface.
		Set<Class<? extends Message>> messages = reflections.getSubTypesOf(Message.class);
		// Instantiate the message classes to get their message id from the
		// method and store it for later serialization and deserialization.
		for (Class<? extends Message> msg : messages) {

			Constructor<? extends Message> cons = msg.getConstructor();

			String key = cons.newInstance().getMessageId();
			
			if(ids.contains(key)) {
				duplicates.add(key);
			} else {
				ids.add(key);
			}
		}
		
		String msg = "Duplicate Message IDs found: " + duplicates.toString();
		
		Assert.assertTrue(msg, duplicates.isEmpty());
	}
}
