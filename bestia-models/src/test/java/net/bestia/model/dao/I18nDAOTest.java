package net.bestia.model.dao;

import net.bestia.model.i18n.I18nRepository;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.model.i18n.I18n;
import net.bestia.model.i18n.TranslationCategory;

//@RunWith(SpringRunner.class)
//@SpringBootTest
//@DataJpaTest
public class I18nDAOTest {

	@Autowired
	private I18nRepository i18NRepository;
	
	//@Test
	public void findById_existingId_dataset() {
		I18n result = i18NRepository.findOne(TranslationCategory.ITEM, "apple.name", "de");
		Assert.assertNotNull(result);
	}
	
	//@Test
	public void findById_noExistingId_null() {
		I18n result = i18NRepository.findOne(TranslationCategory.ITEM, "blabla.name", "de");
		Assert.assertNull(result);
	}
}
