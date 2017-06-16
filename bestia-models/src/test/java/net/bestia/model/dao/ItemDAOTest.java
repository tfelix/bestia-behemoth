package net.bestia.model.dao;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bestia.model.domain.Item;

@RunWith(SpringRunner.class)
@SpringBootTest
@DataJpaTest
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
