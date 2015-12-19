package net.bestia.model.dao;

import static org.junit.Assert.assertEquals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import net.bestia.model.domain.Account;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/spring-config.xml" })
public class AccountDAOTest {

	private int accounts = 0;

	@Autowired
	private AccountDAO accountDao;

	public Account getNewAccount() {
		accounts++;
		String email = "thomas.felix" + accounts + "@tfelix.de";
		Account a = new Account(email, "test123");
		return a;
	}

	@Test
	public void update_test() {
		Account a = getNewAccount();
		accountDao.save(a);
		a.setEmail("max.muser@mann.de");
		accountDao.save(a);

		Account found = accountDao.find(a.getId());
		assertEquals("max.muser@mann.de", found.getEmail());
	}

	@Test
	public void add_test() {
		int oldSize = accountDao.list().size();
		Account a = new Account();
		a.setEmail("hello@world.de");
		accountDao.save(a);
		int newSize = accountDao.list().size();

		assertFalse(oldSize == newSize);
	}

	@Test
	public void remove_test() {
		int oldSize = accountDao.list().size();
		Account a = accountDao.list().get(0);
		accountDao.delete(a);
		int newSize = accountDao.list().size();

		assertFalse(oldSize == newSize);
	}

	@Test
	public void testList() {
		List<Account> list = accountDao.list();
		assertNotNull(list);
		assertFalse(list.isEmpty());
	}

	@Test
	public void findByEmail_test() {
		Account a = new Account("thomas.felix@tfelix.de", "test123");
		accountDao.save(a);

		Account found = accountDao.findByEmail("thomas.felix@tfelix.de");
		assertNotNull(found);
	}
}
