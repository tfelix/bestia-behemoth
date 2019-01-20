package net.bestia.zoneserver.i18n

import mu.KotlinLogging
import net.bestia.model.account.Account
import net.bestia.model.i18n.I18nRepository
import net.bestia.model.i18n.TranslationCategory

private val LOG = KotlinLogging.logger { }

/**
 * Helper class to use translation in an easy way. Uses the i18n DAO to
 * translate messages. No state is kept therefore this class is threadsafe.
 *
 * @author Thomas Felix
 */
class I18nService(
    private val i18NRepository: I18nRepository
) {

  /**
   * Translates the given string with the given arguments. Extracts the set
   * language from the given account.
   *
   * @param acc
   * Account receiving the translation.
   * @param key
   * String key to translate.
   * @param args
   * Arguments to be filled into the translated string.
   * @return The translated string.
   */
  fun t(acc: Account?, key: String, vararg args: Any): String {
    if (acc == null) {
      throw IllegalArgumentException("Account can not be null.")
    }

    return t(acc.language, key, *args)
  }

  fun t(lang: String, key: String, vararg args: Any): String {
    val cat = getCategory(key)

    if (cat == null) {
      val errMsg = String.format("%s-%s", NO_CATEGORY, key)
      LOG.warn(errMsg)
      return errMsg
    }

    val keyClean = removeCategory(key)

    if (keyClean == null) {
      val errMsg = String.format("%s-%s", NO_TRANSLATION, key)
      LOG.warn(errMsg)
      return errMsg
    }

    // Replace the _ from java.Lang with the - version.
    val langClean = lang.replace('_', '-')

    val trans = i18NRepository.findOne(cat, keyClean, langClean)
    if (trans == null) {
      val errMsg = "$NO_TRANSLATION-$key"
      LOG.warn(errMsg)
      return errMsg
    }

    return String.format(trans.value, *args)
  }

  /**
   * Removes the category from the string key. If there was not category the
   * entire key is returned.
   *
   * @param key
   * The key to remove the category from.
   * @return The category removed from the key.
   */
  private fun removeCategory(key: String): String? {
    val keySplit = key.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    return if (keySplit.size < 2) {
      null
    } else {
      keySplit[1]
    }
  }

  /**
   * Returns the category of the given string or null if the category could
   * not been found.
   *
   * @param key
   * The key to extract the category from.
   * @return The found [TranslationCategory] or NULL if no category
   * could be found.
   */
  private fun getCategory(key: String): TranslationCategory? {
    val cat = key.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

    if (cat.size < 2) {
      return null
    }
    return try {
      TranslationCategory.valueOf(cat[0].toUpperCase())
    } catch (ex: IllegalArgumentException) {
      null
    }

  }

  companion object {
    private const val NO_CATEGORY = "NO CATEGORY"
    private const val NO_TRANSLATION = "NO TRANSLATION"
  }
}
