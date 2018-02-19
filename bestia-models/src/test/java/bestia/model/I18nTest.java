package bestia.model;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

import java.util.Locale;

import bestia.model.dao.I18nDAO;
import bestia.model.domain.Account;
import bestia.model.domain.TranslationCategory;

public class I18nTest {

	private static final String t1Result = "HELLO_WORLD";

	private static bestia.model.domain.I18n t1 = new bestia.model.domain.I18n("test", TranslationCategory.ETC,
			"DE-de",
			t1Result);

	@BeforeClass
	public static void setup() {
		I18n.setDao(getI18nDaoMock());
	}

	@Test
	public void t_rightFormat_replacedString() {

		String t1 = I18n.t("de-DE", "etc.test");
		Assert.assertEquals(t1Result, t1);

	}

	@Test
	public void tAccount_rightFormat_replacedString() {
		Account acc = getAccountMock();
		String t1 = I18n.t(acc, "etc.test");
		Assert.assertEquals("Account as param failed.", t1Result, t1);
	}

	@Test
	public void t_wrongFormat_error() {

		final String wrongFormat = "blatest";
		String t1 = I18n.t("de-DE", wrongFormat);
		Assert.assertEquals("NO CATEGORY-blatest", t1);
	}

	@Test
	public void t_unknownCat_error() {

		final String wrongFormat = "bla.test";
		String t1 = I18n.t("de-DE", wrongFormat);
		Assert.assertEquals("NO CATEGORY-bla.test", t1);
	}

	@Test
	public void tAccount_wrongFormat_error() {

		final String wrongFormat = "blatest";
		Account acc = getAccountMock();
		String t1 = I18n.t(acc, wrongFormat);
		Assert.assertEquals("Account as param failed.", "NO CATEGORY-blatest", t1);
	}

	@Test
	public void tAccount_unknownCat_error() {

		final String wrongFormat = "bla.test";
		Account acc = getAccountMock();
		String t1 = I18n.t(acc, wrongFormat);
		Assert.assertEquals("Account as param failed.", "NO CATEGORY-bla.test", t1);

	}

	public static I18nDAO getI18nDaoMock() {
		I18nDAO mock = Mockito.mock(I18nDAO.class);

		when(mock.findOne(TranslationCategory.ETC, "test", "de-DE")).thenReturn(t1);

		return mock;
	}
	
	public Account getAccountMock() {
		Account acc = mock(Account.class);
		
		when(acc.getLanguage()).thenReturn(Locale.GERMANY);
		
		return acc;
	}
}
