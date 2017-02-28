package net.bestia.messages;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.util.ClassUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.bestia.util.PackageLoader;

public class JsonMessageTest {

	private final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Tests if all classes implementing this parent class have a default ctor.
	 */
	@Test
	public void json_defaultCtorPresent() {
		for (Class<? extends JsonMessage> clazz : getJsonClasses()) {
			Assert.assertTrue("Class " + clazz.toGenericString() + " has no default ctor.",
					ClassUtils.hasConstructor(clazz));
		}
	}

	/**
	 * Tests if all classes used in messages are also serializable.
	 */
	@Test
	public void json_allSerilizable() {

		Set<String> notImplementing = new HashSet<>();

		for (Class<? extends JsonMessage> clazz : getJsonClasses()) {

			Field[] fields = clazz.getDeclaredFields();

			for (Field f : fields) {

				if (f.getType().isPrimitive()) {
					continue;
				}

				if (!Serializable.class.isAssignableFrom(f.getType().getClass())) {
					notImplementing.add(f.getType().getClass().getName());
				}
			}

		}

		Assert.assertEquals("The following classes must implement Serializable.", 0, notImplementing.size());
	}

	/**
	 * Checks if the messages can be serialized by jackson. (do they have
	 * message ids and ctor?)
	 */
	@Test
	public void jsonMsg_jacksonSerialize() {
		Set<String> notWorking = new HashSet<>();

		for (Class<? extends JsonMessage> clazz : getJsonClasses()) {

			if (!mapper.canSerialize(clazz)) {
				notWorking.add(clazz.getName());
			}

		}

		Assert.assertEquals("The following classes can not be serialized by jackson: " + notWorking.toString(), 0,
				notWorking.size());
	}

	private Set<Class<? extends JsonMessage>> getJsonClasses() {
		PackageLoader<JsonMessage> loader = new PackageLoader<>(JsonMessage.class, "net.bestia.messages");
		return loader.getSubClasses();
	}
}
