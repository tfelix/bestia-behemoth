package net.bestia.model.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

import net.bestia.model.domain.Account;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseSetup;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/spring-config.xml" })
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class,
    DbUnitTestExecutionListener.class })
@DatabaseSetup("/db/accounts.xml")
public class AccountDAOTest {

	@Autowired
	private AccountDAO accountDao;

	public Account getNewAccount() {
		final Account a = new Account("thomas.felix@tfelix.de", "test123");
		return a;
	}

	@Test
	public void update_test() {
		final Account a = getNewAccount();
		a.setId(1L);
		accountDao.save(a);
		a.setEmail("max.muser@mann.de");
		accountDao.save(a);

		Account found = accountDao.findByEmail("max.muser@mann.de");
		assertNotNull(found);
	}

	@Test
	public void add_test() {
		long oldSize = accountDao.count();
		Account a = new Account();
		a.setEmail("hello@world.de");
		accountDao.save(a);
		long newSize = accountDao.count();

		assertFalse(oldSize == newSize);
	}

	@Test
	public void remove_test() {
		long oldSize = accountDao.count();
		final Account a = accountDao.findAll().iterator().next();
		accountDao.delete(a);
		long newSize = accountDao.count();

		assertFalse(oldSize == newSize);
	}

	@Test
	public void testList() {
		Iterator<Account> list = accountDao.findAll().iterator();
		assertTrue(list.hasNext());
	}

	@Test
	public void findByEmail_test() {
		Account a = new Account("thomas.felix@tfelix.de", "test123");
		accountDao.save(a);

		Account found = accountDao.findByEmail("thomas.felix@tfelix.de");
		assertNotNull(found);
	}
}
