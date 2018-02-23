package net.bestia.model.dao;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bestia.model.domain.Party;

@RunWith(SpringRunner.class)
@SpringBootTest
@DataJpaTest
public class PartyDAOTest {
	
	private final static long ACC_ID = 123L;
	
	@Autowired
	private PartyDAO dao;
	
	@Test
	public void findPartyByMembership_accountIsMember_party() {
		Party p = dao.findPartyByMembership(ACC_ID);
		
		Assert.assertNotNull(p);
	}

}
