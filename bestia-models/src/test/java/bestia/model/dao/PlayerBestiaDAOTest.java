package bestia.model.dao;

import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import bestia.model.domain.Account;
import bestia.model.domain.PlayerBestia;
import bestia.model.domain.ConditionValues;

@RunWith(SpringRunner.class)
@SpringBootTest
@DataJpaTest
public class PlayerBestiaDAOTest {
	
	private final static String BESTIA_NAME = "test";
	private final static String BESTIA_UNKNOWN_NAME = "blablitest";

	@Autowired
	private PlayerBestiaDAO playerDao;
	
	@Before
	public void setup() {
		
		Account acc = new Account();
		acc.setEmail("test@test.net");
		
		PlayerBestia pb = new PlayerBestia();
		
		ConditionValues sv = new ConditionValues();
		sv.setCurrentHealth(10);
		sv.setCurrentMana(10);
			
		pb.setStatusValues(sv);
		pb.setLevel(10);
		pb.setName(BESTIA_NAME);
		
		acc.setMaster(pb);
		pb.setOwner(acc);
		
		playerDao.save(pb);
	}
	
	@Test
	public void findPlayerBestiasForAccount_unknownAcc_null() {
		final Set<PlayerBestia> bestias = playerDao.findPlayerBestiasForAccount(1337);
		Assert.assertTrue(bestias.size() == 0);
	}
	
	@Test
	public void findPlayerBestiasForAccount_knownAcc_bestias() {
		final Set<PlayerBestia> bestias = playerDao.findPlayerBestiasForAccount(1337);
		Assert.assertTrue(bestias.size() == 0);
	}
	
	@Test
	public void findMasterBestiaWithName_knownName_bestia() {
		PlayerBestia pb = playerDao.findMasterBestiaWithName(BESTIA_NAME);
		
		Assert.assertNotNull(pb);
	}
	
	@Test
	public void findMasterBestiaWithName_unknownName_null() {
		PlayerBestia pb = playerDao.findMasterBestiaWithName(BESTIA_UNKNOWN_NAME);
		Assert.assertNull(pb);
	}
}
