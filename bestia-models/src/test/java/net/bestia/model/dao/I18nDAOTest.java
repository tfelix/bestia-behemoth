package net.bestia.model.dao;

import net.bestia.model.domain.I18n;
import net.bestia.model.domain.TranslationCategory;

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
@ContextConfiguration(locations = {"/spring-config.xml"})
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class,
        DbUnitTestExecutionListener.class })
public class I18nDAOTest {

	@Autowired
	private I18nDAO i18nDAO;
	
	@Test
	@DatabaseSetup("/db/i18ns.xml")
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
