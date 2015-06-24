package net.bestia.model;

import static org.junit.Assert.*;

import java.util.Set;

import net.bestia.model.dao.GenericDAO;

import org.junit.Test;
import org.reflections.Reflections;

public class ServiceLocatorTest {

	@SuppressWarnings("rawtypes")
	@Test
	public void get_daos_test() {
		Reflections reflections = new Reflections("net.bestia.model.dao");
		// Get all dao classes.
		Set<Class<? extends GenericDAO>> daoClasses = reflections.getSubTypesOf(GenericDAO.class);

		final ServiceLocator locator = new ServiceLocator();

		for (Class<? extends GenericDAO> daoClass : daoClasses) {
			GenericDAO dao = locator.getBean(daoClass);
			assertNotNull(dao);
		}
	}

	@Test
	public void get_services_test() {
		Reflections reflections = new Reflections("net.bestia.model.service");
		// Get all dao classes.
		Set<Class<? extends Object>> serviceClasses = reflections.getSubTypesOf(Object.class);

		final ServiceLocator locator = new ServiceLocator();

		for (Class<? extends Object> serviceClass : serviceClasses) {
			Object dao = locator.getBean(serviceClass);
			assertNotNull(dao);
		}
	}
}
