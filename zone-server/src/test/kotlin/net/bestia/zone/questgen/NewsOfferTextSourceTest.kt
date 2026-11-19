package net.bestia.zone.questgen

import net.bestia.zone.prose.ProseValidator
import net.bestia.zone.questgen.text.NewsOfferTextSource
import net.bestia.zone.text.TextSubject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewsOfferTextSourceTest {

  private val source = NewsOfferTextSource(QuestgenConfig(newsTextsPerKind = 3))
  private val validator = ProseValidator()

  @Test
  fun `a few texts are wanted for each kind of news that is carried, each in another voice`() {
    val wanted = source.wanted().map { it.subject }

    assertEquals(6, wanted.size)
    assertTrue(TextSubject.newsOffer("TOWN_FELL", 2) in wanted && TextSubject.newsOffer("BOSS_SLAIN", 0) in wanted, "$wanted")
    assertEquals(6, wanted.map { source.briefOf(it)!! }.distinctBy { it.task to it.facts }.size)
  }

  @Test
  fun `a text must leave the town open, and may name none of its own`() {
    val brief = assertNotNull(source.briefOf(TextSubject.newsOffer("TOWN_FELL", 0)))

    val good = "A town near us is gone, and no house is left standing. Some got away. Will you carry the word to {town}?"
    assertEquals(emptyList(), validator.check(good, brief).map { it.value })

    val named = "A town near us is gone. Will you carry the word to Dunmere?"
    assertEquals(listOf("Dunmere", "{town}"), validator.check(named, brief).map { it.value })
  }

  @Test
  fun `news nobody carries, and a subject of another source, have no brief`() {
    assertNull(source.briefOf(TextSubject.newsOffer("MISDEED_SEEN", 0)))
    assertNull(source.briefOf(TextSubject.grave(3)))
  }
}
