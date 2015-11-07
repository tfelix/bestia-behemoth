package net.bestia.model;

import org.junit.Assert;
import org.junit.BeforeClass;

import net.bestia.model.dao.I18nDAO;
import net.bestia.model.domain.Account;

public class I18nTest {

	private static final String t1Result = "HELLO_WORLD";

	@BeforeClass
	public static void setup() {
		I18n.setDao(getI18nDaoMock());
	}

	public void t_rightFormat_replacedString() {
		
		String t1 = I18n.t("DE-de", "test.test");
		Assert.assertEquals(t1Result, t1);

		Account acc = null;
		t1 = I18n.t(acc, "test.test");
		Assert.assertEquals("Account as param failed.", t1Result, t1);
	}

	public void t_wrongFormat_originalString() {
		
		final String wrongFormat = "blatest";
		String t1 = I18n.t("DE-de", wrongFormat);
		Assert.assertEquals(wrongFormat, t1);
	}

	public void t_unknownCat_originalString() {

		final String wrongFormat = "blatest";
		String t1 = I18n.t("DE-de", wrongFormat);
		Assert.assertEquals(wrongFormat, t1);

	}

	public void tAccount_wrongFormat_originalString() {
		
		final String wrongFormat = "blatest";
		Account acc = null;
		// TODO
		String t1 = I18n.t(acc, wrongFormat);
		Assert.assertEquals("Account as param failed.", wrongFormat, t1);
	}

	public void tAccount_unknownCat_originalString() {

		final String wrongFormat = "bla.test";
		Account acc = null;
		// TODO
		String t1 = I18n.t(acc, wrongFormat);
		Assert.assertEquals("Account as param failed.", wrongFormat, t1);

	}

	public static I18nDAO getI18nDaoMock() {
		// TODO
		return null;
	}
}
