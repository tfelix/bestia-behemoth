package net.bestia.model;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import net.bestia.model.dao.GenericDAO;
import net.bestia.model.dao.MemoryDao;

public class AccountDAOTest {

	private GenericDAO<Account, Long> accountDao = new MemoryDao<>();
	
	@Before
	public void setUp() {
		for(int i = 0; i < 5; i++) {
			Account a = new Account();
			a.setEmail("account" + i + "@example.com");
			accountDao.save(a);
		}
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
		Account a = accountDao.find(1L);
		accountDao.delete(a);
		int newSize = accountDao.list().size();
		
		assertFalse(oldSize == newSize);
	}
	
	@Test
    public void testList() {
        List<Account> list = accountDao.list();
        assertNotNull (list);
        assertFalse (list.isEmpty());
    }
}
