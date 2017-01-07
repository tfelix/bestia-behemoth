package net.bestia.messages;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.util.ClassUtils;

import net.bestia.util.PackageLoader;

public class JsonMessageTest {

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
			
			for(Field f : fields) {
				
				if(f.getType().isPrimitive()) {
					continue;
				}
				
				if(!Serializable.class.isAssignableFrom(f.getType().getClass())) {
					notImplementing.add(f.getType().getClass().getName());
				}
			}
			
		}
		
		Assert.assertEquals("The following classes must implement Serializable.", 0, notImplementing.size());
	}
	
	private Set<Class<? extends JsonMessage>> getJsonClasses() {
		PackageLoader<JsonMessage> loader = new PackageLoader<>(JsonMessage.class, "net.bestia.messages");
		return loader.getSubClasses();
	}
}
