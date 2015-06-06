package net.bestia.model;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.AccountDAOHibernate;

import static org.junit.Assert.*;

import org.junit.Test;

public class ServiceLocatorTest {

	@Test
	public void get_accountDao_test() {
		ServiceLocator locator = new ServiceLocator();
		AccountDAO accDao = locator.getObject(AccountDAO.class);	
		assertNotNull(accDao);
	}
}
