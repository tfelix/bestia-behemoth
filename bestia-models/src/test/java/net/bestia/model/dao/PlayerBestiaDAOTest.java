package net.bestia.model.dao;

import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import com.github.springtestdbunit.DbUnitTestExecutionListener;

import net.bestia.model.domain.PlayerBestia;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/spring-config.xml" })
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class, DbUnitTestExecutionListener.class })
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
