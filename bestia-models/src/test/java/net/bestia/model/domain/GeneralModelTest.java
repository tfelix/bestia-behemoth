package net.bestia.model.domain;

import java.io.Serializable;
import java.util.Set;

import javax.persistence.Entity;

import org.junit.Assert;
import org.junit.Test;
import org.reflections.Reflections;

public class GeneralModelTest {

	
	/**
	 * All entities must implement serializable.
	 */
	@Test
	public void all_serializable_test() {
		Reflections reflections = new Reflections("net.bestia.model");
		Set<Class<?>> allClasses = reflections.getTypesAnnotatedWith(Entity.class);
		
		for(Class<?> clazz : allClasses) {
			Assert.assertTrue(Serializable.class.isAssignableFrom(clazz));
		}		
	}
}
