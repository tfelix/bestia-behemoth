package net.bestia.model.dao;

import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.model.domain.I18n;
import net.bestia.model.domain.TranslationCategory;

//@RunWith(SpringRunner.class)
//@SpringBootTest
//@DataJpaTest
public class I18nDAOTest {

	@Autowired
	private I18nDAO i18nDAO;
	
	//@Test
	public void findById_existingId_dataset() {
		I18n result = i18nDAO.findOne(TranslationCategory.ITEM, "apple.name", "de");
		Assert.assertNotNull(result);
	}
	
	//@Test
	public void findById_noExistingId_null() {
		I18n result = i18nDAO.findOne(TranslationCategory.ITEM, "blabla.name", "de");
		Assert.assertNull(result);
	}
}
