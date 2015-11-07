package net.bestia.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.model.dao.I18nDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.TranslationCategory;

/**
 * Helper class to use translation in an easy way. Uses the i18n DAO to
 * translate messages. No state is kept therefore this class is threadsafe.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class I18n {

	private static final Logger LOG = LogManager.getLogger(I18n.class);
	private static I18nDAO i18nDao = null;

	private static final String NO_CATEGORY = "NO CATEGORY";
	private static final String NO_TRANSLATION = "NO TRANSLATION";

	/**
	 * Use this class in a static fashion to translate strings.
	 */
	private I18n() {
		// no op.
	}

	public static void setDao(I18nDAO dao) {
		if (dao == null) {
			throw new IllegalArgumentException("I18nDAO can not be null.");
		}
		I18n.i18nDao = dao;
	}

	/**
	 * Translates the given string with the given arguments. Extracts the set
	 * language from the given account.
	 * 
	 * @param acc
	 *            Account receiving the translation.
	 * @param key
	 *            String key to translate.
	 * @param args
	 *            Arguments to be filled into the translated string.
	 * @return The translated string.
	 */
	public static String t(Account acc, String key, Object... args) {
		if (acc == null) {
			throw new IllegalArgumentException("Account can not be null.");
		}

		return t(acc.getLanguage().toString(), key, args);
	}

	public static String t(String lang, String key, Object... args) {
		// Translate the string with the database.
		if (I18n.i18nDao == null) {
			throw new IllegalStateException("Please call setDao() first.");
		}

		final TranslationCategory cat = getCategory(key);

		if (cat == null) {
			final String errMsg = String.format("%s-%s", NO_CATEGORY, key);
			LOG.warn(errMsg);
			return errMsg;
		}

		final String keyClean = removeCategory(key);

		if (keyClean == null) {
			final String errMsg = String.format("%s-%s", NO_TRANSLATION, key);
			LOG.warn(errMsg);
			return errMsg;
		}

		// Replace the _ from java.Lang with the - version.
		lang = lang.replace('_', '-');
		
		final net.bestia.model.domain.I18n trans = I18n.i18nDao.findOne(cat, keyClean, lang);
		if (trans == null) {
			final String errMsg = String.format("%s-%s", NO_TRANSLATION, key);
			LOG.warn(errMsg);
			return errMsg;
		}

		return String.format(trans.getValue(), args);
	}

	/**
	 * Removes the category from the string key. If there was not category the
	 * entire key is returned.
	 * 
	 * @param key
	 *            The key to remove the category from.
	 * @return The category removed from the key.
	 */
	private static String removeCategory(String key) {
		final String[] keySplit = key.split("\\.");
		if (keySplit.length < 2) {
			return null;
		} else {
			return keySplit[1];
		}
	}

	/**
	 * Returns the category of the given string or null if the category could
	 * not been found.
	 * 
	 * @param key
	 *            The key to extract the category from.
	 * @return The found {@link TranslationCategory} or NULL if no category
	 *         could be found.
	 */
	private static TranslationCategory getCategory(String key) {
		final String[] cat = key.split("\\.");

		if (cat.length < 2) {
			return null;
		}
		try {
			final TranslationCategory tCat = TranslationCategory.valueOf(cat[0].toUpperCase());
			return tCat;
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
