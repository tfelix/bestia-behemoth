package net.bestia.model.dao;

import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bestia.model.domain.PlayerBestia;

@RunWith(SpringRunner.class)
@SpringBootTest
@DataJpaTest
public class PlayerBestiaDAOTest {

	@Autowired
	private PlayerBestiaDAO playerDao;
	
	@Test
	public void findPlayerBestiasForAccount_unknownAcc_null() {
		final Set<PlayerBestia> bestias = playerDao.findPlayerBestiasForAccount(1337);
		Assert.assertTrue(bestias.size() == 0);
	}
	
	@Test
	public void savePlayerBestiasForAccount_success() {
		
		//PlayerBestia pb = new PlayerBestia(owner, origin)
		//Assert.assertTrue(bestias.size() == 0);
	}
}
