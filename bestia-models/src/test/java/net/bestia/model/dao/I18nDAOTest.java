package net.bestia.model.dao;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bestia.model.domain.I18n;
import net.bestia.model.domain.TranslationCategory;

@RunWith(SpringRunner.class)
@SpringBootTest
@DataJpaTest
public class I18nDAOTest {

	@Autowired
	private I18nDAO i18nDAO;
	
	@Test
	public void findById_existingId_dataset() {
		I18n result = i18nDAO.findOne(TranslationCategory.ITEM, "apple.name", "de");
		Assert.assertNotNull(result);
	}
	
	@Test
	public void findById_noExistingId_null() {
		I18n result = i18nDAO.findOne(TranslationCategory.ITEM, "blabla.name", "de");
		Assert.assertNull(result);
	}
}
