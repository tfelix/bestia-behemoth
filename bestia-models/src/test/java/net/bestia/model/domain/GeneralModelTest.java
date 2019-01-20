package net.bestia.model.domain;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Entity;

import net.bestia.model.account.LoginInfo;
import org.junit.Assert;
import org.junit.Test;
import org.reflections.Reflections;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GeneralModelTest {

	private ObjectMapper mapper = new ObjectMapper();
	private Reflections reflections = new Reflections("net.bestia.model");

	private final static Set<Class<? extends Object>> WHITELIST = new HashSet<>(Arrays.asList(LoginInfo.class));

	private Set<Class<?>> getAllEntities() {
		final Set<Class<?>> allClasses = reflections
				.getTypesAnnotatedWith(Entity.class);
		return allClasses;
	}
	
	public GeneralModelTest() {
		
		// Ignore null fields for the sake of the tests.
		mapper.setSerializationInclusion(Include.NON_NULL);
	}

	/**
	 * All entities must implement serializable.
	 */
	@Test
	public void all_serializable() {
		for (Class<?> clazz : getAllEntities()) {

			// Whitelist classes dont need to be serializable.
			if (WHITELIST.contains(clazz)) {
				continue;
			}

			Assert.assertTrue(clazz.toGenericString()
					+ " does not implement Serializable.",
					Serializable.class.isAssignableFrom(clazz));
		}
	}

	/**
	 * All entities must implement serializable.
	 * 
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 */
	@Test
	public void all_std_ctor() throws InstantiationException, IllegalAccessException {
		for (Class<?> clazz : getAllEntities()) {
			clazz.newInstance();
		}
	}

	/**
	 * All models should be serializable to JSON.
	 */
	@Test
	public void all_serializable_json() throws Exception {
		for (Class<?> clazz : getAllEntities()) {

			// Whitelist classes dont need to be serializable.
			if (WHITELIST.contains(clazz)) {
				continue;
			}

			Object obj = clazz.newInstance();
			mapper.writeValueAsString(obj);
		}
	}
}
