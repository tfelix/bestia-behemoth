package net.bestia.zoneserver.i18n

import net.bestia.model.i18n.I18n
import net.bestia.model.i18n.I18nRepository
import net.bestia.model.account.Account
import net.bestia.model.i18n.TranslationCategory
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever

import java.util.Locale

class I18nTest {
  private lateinit var accountMock: Account
  private lateinit var i18nRepository: I18nRepository

  private lateinit var i18n: I18nService

  @Before
  fun setup() {
    i18nRepository = mock { }
    whenever(i18nRepository.findOne(TranslationCategory.ETC, "test", "de-DE")).thenReturn(t1)

    accountMock = mock { }
    whenever(accountMock.language).thenReturn(Locale.GERMANY.toString())

    i18n = I18nService(i18nRepository)
  }

  @Test
  fun t_rightFormat_replacedString() {
    val t1 = i18n.t("de-DE", "etc.test")
    Assert.assertEquals(t1Result, t1)
  }

  @Test
  fun tAccount_rightFormat_replacedString() {
    val acc = accountMock
    val t1 = i18n.t(acc, "etc.test")
    Assert.assertEquals("Account as param failed.", t1Result, t1)
  }

  @Test
  fun t_wrongFormat_error() {

    val wrongFormat = "blatest"
    val t1 = i18n.t("de-DE", wrongFormat)
    Assert.assertEquals("NO CATEGORY-blatest", t1)
  }

  @Test
  fun t_unknownCat_error() {
    val wrongFormat = "bla.test"
    val t1 = i18n.t("de-DE", wrongFormat)
    Assert.assertEquals("NO CATEGORY-bla.test", t1)
  }

  @Test
  fun tAccount_wrongFormat_error() {
    val wrongFormat = "blatest"
    val acc = accountMock
    val t1 = i18n.t(acc, wrongFormat)
    Assert.assertEquals("Account as param failed.", "NO CATEGORY-blatest", t1)
  }

  @Test
  fun tAccount_unknownCat_error() {
    val wrongFormat = "bla.test"
    val acc = accountMock
    val t1 = i18n.t(acc, wrongFormat)
    Assert.assertEquals("Account as param failed.", "NO CATEGORY-bla.test", t1)

  }

  companion object {
    private const val t1Result = "HELLO_WORLD"
    private val t1 = I18n(
        "test",
        TranslationCategory.ETC,
        "DE-de",
        t1Result
    )
  }
}
