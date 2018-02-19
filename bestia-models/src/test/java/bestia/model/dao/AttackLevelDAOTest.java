package bestia.model.dao;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseSetup;

import bestia.model.domain.BestiaAttack;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class,
   DbUnitTestExecutionListener.class })
@DatabaseSetup("/db/attack_levels.xml")
@DataJpaTest
public class AttackLevelDAOTest {
	
	@Autowired
	private BestiaAttackDAO attackLevelDao;
	
	@Test
	public void getAllAttacksForBestia_existingId_list() {
		final List<BestiaAttack> atks = attackLevelDao.getAllAttacksForBestia(1);
		Assert.assertNotNull(atks);
		Assert.assertEquals(1, atks.size());
	}
	
	@Test
	public void getAllAttacksForBestia_notExistingId_null() {
		final List<BestiaAttack> atks = attackLevelDao.getAllAttacksForBestia(1337);
		Assert.assertTrue(atks.isEmpty());
		
	}

}
