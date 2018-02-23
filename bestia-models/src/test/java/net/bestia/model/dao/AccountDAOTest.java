package net.bestia.model.dao;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bestia.model.domain.Account;
import net.bestia.model.domain.Bestia;
import net.bestia.model.domain.PlayerBestia;

@RunWith(SpringRunner.class)
@SpringBootTest
/*@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class,
    DbUnitTestExecutionListener.class })
@DatabaseSetup("/db/accounts.xml")*/
@DataJpaTest
public class AccountDAOTest {
	
	private static final String EMAIL = "test@test.net";

	@Autowired
	private AccountDAO accountDao;

	public Account getNewAccount() {
		final Account a = new Account(EMAIL, "test123");
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
		
		Account a = new Account(EMAIL, "test123");
		accountDao.save(a);
		
		long accId = a.getId();

		accountDao.delete(a);
		
		a = accountDao.findOne(accId);

		Assert.assertNull(a);
	}

	@Test
	public void findByEmail_test() {
		Account a = new Account(EMAIL, "test123");
		accountDao.save(a);

		Account found = accountDao.findByEmail(EMAIL);
		assertNotNull(found);
	}
	
	@Test
	public void findByEmailOrUsername_findsBoth() {
		Account a = new Account(EMAIL, "test123");
		
		Bestia b = new Bestia();
		PlayerBestia pb = new PlayerBestia(a, b);
		pb.setName("max");
		a.setMaster(pb);
		
		accountDao.save(a);
		
		Account found = accountDao.findByUsernameOrEmail(EMAIL);
		assertNotNull(found);
		
		found = accountDao.findByUsernameOrEmail("max");
		assertNotNull(found);
	}
}
