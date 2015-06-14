package net.bestia.model.domain;

import org.junit.Test;

public class GeneralModelTest {

	/**
	 * TO avoid a duplicate key entry exception new entities should instanciated with a value different from 0 in order
	 * to get detected as an update by hibernate.
	 */
	@Test
	public void primary_key_default_is_null() {

	}
}
