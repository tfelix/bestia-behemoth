package net.bestia.model.dao;

import net.bestia.model.domain.Item;

import org.junit.Assert;
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
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class, DbUnitTestExecutionListener.class })
@DatabaseSetup("/db/items.xml")
public class ItemDAOTest {

	@Autowired
	private ItemDAO itemDao;

	@Test
	public void findItemByName_existingName_item() {
		final Item item = itemDao.findItemByName("apple");
		Assert.assertNotNull(item);
	}

	@Test
	public void findItemByName_nonExistingName_null() {
		final Item item = itemDao.findItemByName("bla_bli_blub");
		Assert.assertNull(item);
	}
}
