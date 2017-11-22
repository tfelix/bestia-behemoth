package net.bestia.behemoth.translateserver.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The rest controller takes incoming translation keys and translates them into usable strings for the client
 * to consume.
 */
@RestController
public class I18nController {

	/**
	 * Translates the string key upon user request.
	 *
	 * @param langKey The ISO 639-1 language code of the target language.
	 * @param strKey  The key to get translated into the target language.
	 * @return The translated string or an english (default) string as fallback if no language abberative could be found.
	 */
	@RequestMapping(value = "/i18n/{langKey}/{strKey}")
	@ResponseBody
	public I18nKeyValue getTranslationOfKey(@PathVariable String langKey, @PathVariable String strKey) {

		return new I18nKeyValue("test", "test2");

	}
}
