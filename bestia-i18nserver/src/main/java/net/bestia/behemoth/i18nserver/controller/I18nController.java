package net.bestia.behemoth.i18nserver.controller;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import net.bestia.model.dao.I18nDAO;
import net.bestia.model.domain.I18n;
import net.bestia.model.domain.TranslationCategory;

/**
 * The rest controller takes incoming translation keys and translates them into
 * usable strings for the client to consume.
 */
@RestController
public class I18nController {

	@Autowired
	private I18nDAO i18nDao;

	/**
	 * Translates the string key upon user request.
	 *
	 * @param langKey
	 *            The ISO 639-1 language code of the target language.
	 * @param strKey
	 *            The key to get translated into the target language.
	 * @return The translated string or an english (default) string as fallback
	 *         if no language abberative could be found.
	 */
	@RequestMapping(value = "/i18n/{langKey}/{strKey}")
	@ResponseBody
	public I18nKeyValue getTranslationOfKey(@PathVariable String langKey, @PathVariable String strKey) {

		final String[] strToken = strKey.split(".");

		if (strToken.length <= 1) {
			throw new ResourceNotFoundException();
		}

		// Split it into the category and string key.
		final String categoryStr = Stream.of(strToken).findFirst().get();
		final String key = Stream.of(strToken).skip(1).collect(Collectors.joining());

		final TranslationCategory category = TranslationCategory.valueOf(categoryStr);
		
		final I18n translation = i18nDao.findOne(category, key, langKey);

		return new I18nKeyValue(langKey, translation.getValue());

	}
}
