package net.bestia.worldgen.history

import net.bestia.worldgen.civ.Culture
import net.bestia.worldgen.core.ArtifactKind
import net.bestia.worldgen.core.BeastKind
import net.bestia.worldgen.core.CultKind
import net.bestia.worldgen.core.DeityRecord
import net.bestia.worldgen.core.Domain
import net.bestia.worldgen.core.Faction
import net.bestia.worldgen.core.FigureRole
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.core.WorldBoss
import net.bestia.worldgen.place.RegionKind
import kotlin.test.Test
import kotlin.test.assertEquals

class GermanNamesTest {

  @Test
  fun `every English word has a German one`() {
    val styles = Names.STYLES
    val siteForms = SiteKind.entries.flatMap { kind ->
      (Faction.entries + null).map { Names.siteForm(kind, it) }
    }.toSet() - "wound" + RegionKind.entries.map { it.form }

    assertSameWords(Names.BYNAMES.values.flatten(), GermanNames.BYNAMES.keys, "bynames")
    assertSameWords(Names.DEITY_TITLES.values.flatten(), GermanNames.DEITY_TITLES.keys, "deity titles")
    assertSameWords(Names.CULT_BANDS, GermanNames.CULT_BANDS.keys, "cult bands")
    assertSameWords(BeastKind.entries, GermanNames.BEASTS.keys, "cult beasts")
    assertSameWords(WorldBoss.entries, GermanNames.BOSS_TITLES.keys, "world boss titles")
    assertSameWords(styles.flatMap { it.peopleTails }, GermanNames.PEOPLE_TAILS.keys, "people tails")
    assertSameWords(styles.flatMap { it.epithets }, GermanNames.EPITHETS.keys, "epithets")
    assertSameWords(Names.ARTIFACT_NOUNS.values.flatten(), GermanNames.ARTIFACT_NOUNS.keys, "artifact nouns")
    assertSameWords(siteForms, GermanNames.SITE_FORMS.keys, "site and region forms")
    assertSameWords(Names.WOUND_EPITHETS, GermanNames.WOUND_EPITHETS.keys, "wound epithets")
    assertSameWords(Names.WOUND_NOUNS, GermanNames.WOUND_NOUNS.keys, "wound nouns")
  }

  @Test
  fun `a byname agrees with the sex of its bearer`() {
    val highland = Culture.indexOf(Culture.HIGHLAND)

    val kunimund = seedWhere { Names.person(it, highland, FigureRole.RULER) == "Kunimund the Reluctant" }
    assertEquals("Kunimund der Widerwillige", Names.person(kunimund, highland, FigureRole.RULER, NameLocale.DE))

    val berta = seedWhere { Names.person(it, highland, FigureRole.RULER) == "Berta the Reluctant" }
    assertEquals("Berta die Widerwillige", Names.person(berta, highland, FigureRole.RULER, NameLocale.DE))

    val lawgiver = seedWhere { Names.person(it, highland, FigureRole.RULER) == "Hedda the Lawgiver" }
    assertEquals("Hedda die Gesetzgeberin", Names.person(lawgiver, highland, FigureRole.RULER, NameLocale.DE))
  }

  @Test
  fun `a god's cult names it in the genitive`() {
    val agrarian = Culture.indexOf(Culture.AGRARIAN)
    val hilda = seedWhere { Names.deity(it, agrarian, Domain.LOVE) == "Hilda the Fair" }
    val god = DeityRecord(0, Domain.LOVE, hilda, year = 1, cause = -1)

    assertEquals("Hilda die Schöne", Names.deity(hilda, agrarian, Domain.LOVE, NameLocale.DE))
    val children = seedWhere {
      Names.cult(it, CultKind.DEITY, "Hilda the Fair", null) == "the Children of Hilda the Fair"
    }
    assertEquals(
      "die Kinder der Hilda der Schönen",
      Names.cult(children, CultKind.DEITY, god, agrarian, null, NameLocale.DE)
    )
  }

  @Test
  fun `places stay as they are and the words around them are German`() {
    val agrarian = Culture.indexOf(Culture.AGRARIAN)
    val town = Names.place(1L, agrarian)

    assertEquals("das Hügelgrab von $town", Names.site(2L, town, "barrow", NameLocale.DE))
    assertEquals("das Schlachtfeld von $town", Names.site(2L, town, "battlefield", NameLocale.DE))

    val sabre = seedWhere { Names.artifact(it, agrarian, ArtifactKind.BLADE, 1L) == "the Sabre of $town" }
    assertEquals("der Säbel von $town", Names.artifact(sabre, agrarian, ArtifactKind.BLADE, 1L, NameLocale.DE))

    val vale = seedWhere { Names.region(it, agrarian, RegionKind.VALE.form) == "Elm Vale" }
    assertEquals("Elmtal", Names.region(vale, agrarian, RegionKind.VALE.form, locale = NameLocale.DE))
  }

  @Test
  fun `the English cult of a god record is the cult of its rendered name`() {
    for (index in 0 until SEEDS) {
      val seed = Names.seedOf(SWEEP_SEED, index.toLong())
      val god = DeityRecord(0, Domain.entries[index % Domain.entries.size], Names.seedOf(seed, 1L), 1, -1)
      for (culture in Culture.ALL.indices) {
        for (kind in CultKind.entries) {
          val named = Names.cult(seed, kind, Names.deity(god.nameSeed, culture, god.domain), BeastKind.BIRD)
          assertEquals(named, Names.cult(seed, kind, god, culture, BeastKind.BIRD, NameLocale.EN))
        }
      }
    }
  }

  private fun assertSameWords(english: Collection<Any>, german: Set<Any>, table: String) {
    assertEquals(english.toSet(), german, "$table: German keys differ from the English words")
  }

  private fun seedWhere(wanted: (Long) -> Boolean): Long {
    return (0L until SEARCH).asSequence().map { Names.seedOf(SWEEP_SEED, it) }.first(wanted)
  }

  private companion object {
    const val SWEEP_SEED = 0x44455554L
    const val SEEDS = 200
    const val SEARCH = 200_000L
  }
}
