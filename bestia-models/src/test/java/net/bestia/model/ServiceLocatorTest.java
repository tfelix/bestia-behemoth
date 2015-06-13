package net.bestia.model;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.AccountDAOHibernate;

import static org.junit.Assert.*;

import org.junit.Test;

public class ServiceLocatorTest {

	@Test
	public void get_accountDao_test() {
		DAOLocator locator = new DAOLocator();
		AccountDAO accDao = locator.getDAO(AccountDAO.class);	
		assertNotNull(accDao);
	}
}
