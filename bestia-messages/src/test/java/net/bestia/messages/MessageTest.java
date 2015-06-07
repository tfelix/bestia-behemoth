package net.bestia.messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.reflections.Reflections;

public class MessageTest {

	/**
	 * Look if every message class has an std. ctor and can be instantiated. Important for jackson serialization.
	 */
	@Test
	public void std_ctor_present_test() {
		Reflections reflections = new Reflections("net.bestia.messages");
		Set<Class<? extends Message>> subTypes = reflections.getSubTypesOf(Message.class);
		
		List<String> notInstances = new ArrayList<>();
		
		for(Class<? extends Message> clazz : subTypes) {
			try{
				clazz.newInstance();
			} catch(Exception ex) {
				notInstances.add(clazz.getName());
			}
		}
		
		String msg = "Could not instanciated: " + notInstances.toString();

		Assert.assertTrue(msg, notInstances.isEmpty());
	}
}
