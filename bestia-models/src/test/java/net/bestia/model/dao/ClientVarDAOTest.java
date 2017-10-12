package net.bestia.model.dao;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bestia.model.domain.Account;
import net.bestia.model.domain.ClientVar;

@RunWith(SpringRunner.class)
@SpringBootTest
@DataJpaTest
public class ClientVarDAOTest {

	@Autowired
	private ClientVarDAO cvDao;
	
	@Autowired
	private AccountDAO accDao;

	private static final long ACC_ID = 1337;
	private static final String EXISTING_KEY = "test";

	@Before
	public void setup() {
		Account acc = new Account();
		acc.setId(ACC_ID);
		accDao.save(acc);
		
		ClientVar cv = new ClientVar(acc, EXISTING_KEY, "test1234");
		cvDao.save(cv);
	}

	@Test
	public void findByKeyAndAccountId_validKeyAndAccId_finds() {
		ClientVar cv = cvDao.findByKeyAndAccountId(EXISTING_KEY, ACC_ID);
		Assert.assertNotNull(cv);
	}

	@Test
	public void deleteByKeyAndAccountId_validKeyAndAccId_deletes() {
		cvDao.deleteByKeyAndAccountId(EXISTING_KEY, ACC_ID);
		ClientVar cv = cvDao.findByKeyAndAccountId(EXISTING_KEY, ACC_ID);
		Assert.assertNull(cv);
	}
}
