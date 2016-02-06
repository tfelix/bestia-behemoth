package net.bestia.model;

import static org.junit.Assert.fail;

import java.util.Set;

import org.junit.Test;
import org.reflections.Reflections;

public class ServiceLocatorTest {
	
	public static final String DAO_REGEX = "^\\w+DAO$";

	@Test
	public void get_daos() {
		final Reflections reflections = new Reflections("net.bestia.model.dao");
		Set<Class<? extends Object>> daoClasses = reflections.getSubTypesOf(Object.class);
		

		final ServiceLocator locator = ServiceLocator.getInstance();

		for (Class<? extends Object> daoClass : daoClasses) {
			try {
				locator.getBean(daoClass);
			} catch(Exception e) {
				fail("DAO missing for: " + daoClass.getCanonicalName()+" "+e.getMessage());
			}
		}
	}

	@Test
	public void get_services() {
		final Reflections reflections = new Reflections("net.bestia.model.service");
		// Get all dao classes.
		Set<Class<? extends Object>> serviceClasses = reflections.getSubTypesOf(Object.class);

		final ServiceLocator locator = ServiceLocator.getInstance();

		for (Class<? extends Object> serviceClass : serviceClasses) {
			try {
				locator.getBean(serviceClass);
			} catch(Exception e) {
				
			}
		}
	}
}
