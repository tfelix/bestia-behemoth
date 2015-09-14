package net.bestia.model;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

import net.bestia.model.dao.GenericDAO;

import org.junit.Test;
import org.reflections.Reflections;

public class ServiceLocatorTest {
	
	public static final String DAO_REGEX = "^\\w+DAO$";

	@SuppressWarnings("rawtypes")
	public void get_daos() {
		Reflections reflections = new Reflections("net.bestia.model.dao");
		// Get all dao classes.
		Set<Class<? extends GenericDAO>> daoClasses = reflections.getSubTypesOf(GenericDAO.class);
		Set<Class<? extends GenericDAO>> filteredDaoClasses = new HashSet<>();
		
		for (Class<? extends GenericDAO> daoClass : daoClasses) {
			final String name = daoClass.getSimpleName();
			if(name.matches(DAO_REGEX)) {
				filteredDaoClasses.add(daoClass);
			}
		}

		final ServiceLocator locator = ServiceLocator.getInstance();

		for (Class<? extends GenericDAO> daoClass : filteredDaoClasses) {
			GenericDAO dao = locator.getBean(daoClass);
			assertNotNull("DAO missing for: " + daoClass.getCanonicalName() , dao);
		}
	}


	public void get_services() {
		Reflections reflections = new Reflections("net.bestia.model.service");
		// Get all dao classes.
		Set<Class<? extends Object>> serviceClasses = reflections.getSubTypesOf(Object.class);

		final ServiceLocator locator = ServiceLocator.getInstance();

		for (Class<? extends Object> serviceClass : serviceClasses) {
			Object dao = locator.getBean(serviceClass);
			assertNotNull("Service missing for: " + serviceClass.getCanonicalName(), dao);
		}
	}
}
