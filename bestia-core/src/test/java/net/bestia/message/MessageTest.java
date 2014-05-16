package net.bestia.message;

import java.lang.reflect.Constructor;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.bestia.core.message.Message;

import org.junit.Assert;
import org.reflections.Reflections;

// Check ob alle Messages eine andere ID besitzen.
public class MessageTest {

	public void general_message_test() throws Exception {

		List<Integer> ids = new LinkedList<>();

		// Find all classes implementing the message interface.
		Reflections reflections = new Reflections("net.bestia");
		Set<Class<? extends Message>> messages = reflections
				.getSubTypesOf(Message.class);
		// Instantiate the message classes to get their message id from the
		// method and store
		// it for later serialization and deserialization.
		for (Class<? extends Message> msg : messages) {

			Constructor<? extends Message> cons = msg.getConstructor();

			int key = cons.newInstance().getMessageId();

			Assert.assertFalse(ids.contains(key));
			ids.add(key);
		}
	}
}
