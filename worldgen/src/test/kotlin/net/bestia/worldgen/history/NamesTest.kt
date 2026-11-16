package net.bestia.worldgen.history

import net.bestia.worldgen.civ.Culture
import net.bestia.worldgen.core.ArtifactKind
import net.bestia.worldgen.core.BeastKind
import net.bestia.worldgen.core.CultKind
import net.bestia.worldgen.core.Domain
import net.bestia.worldgen.core.FigureRole
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.Sex
import net.bestia.worldgen.core.WorldBoss
import net.bestia.worldgen.place.RegionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NamesTest {

  @Test
  fun `every English name is what it was`() {
    // A change to how names are built must not rename an existing world. Re-pin only for a rename you mean.
    assertEquals(ENGLISH_DIGEST, englishDigest())
  }

  @Test
  fun `no given name is tagged with both sexes`() {
    val tags = CULTURES.flatMap { Names.givenNamesOf(it) }.groupBy({ it.name }, { it.sex })
    val ambiguous = tags.filterValues { it.distinct().size > 1 }.keys

    assertTrue(ambiguous.isEmpty(), "tagged both female and male: $ambiguous")
  }

  @Test
  fun `every culture has given names of both sexes`() {
    for (culture in CULTURES) {
      val sexes = Names.givenNamesOf(culture).map { it.sex }.toSet()
      assertEquals(Sex.entries.toSet(), sexes, "culture $culture")
    }
  }

  @Test
  fun `a figure's sex is the tag of the given name it bears`() {
    for (index in 0 until SEEDS) {
      val seed = Names.seedOf(SWEEP_SEED, index.toLong())
      for (culture in CULTURES) {
        val given = Names.person(seed, culture, FigureRole.RULER).substringBefore(' ').lowercase()
        val tagged = Names.givenNamesOf(culture).single { it.name == given }

        assertEquals(tagged.sex, Names.personSex(seed, culture), given)
      }
    }
  }

  private fun englishDigest(): Long {
    var digest = 0L
    fun fold(name: String) {
      digest = GenRng.hash(digest, name.hashCode().toLong(), name.length.toLong())
    }

    for (index in 0 until SEEDS) {
      val seed = Names.seedOf(SWEEP_SEED, index.toLong())
      val other = Names.seedOf(SWEEP_SEED, index.toLong(), 1L)
      for (culture in CULTURES) {
        fold(Names.place(seed, culture))
        fold(Names.townsperson(seed, culture))
        fold(Names.house(seed, culture))
        fold(Names.houseStem(seed, culture))
        fold(Names.civ(seed, culture))
        for (role in FigureRole.entries) fold(Names.person(seed, culture, role))
        for (domain in Domain.entries) fold(Names.deity(seed, culture, domain))
        for (kind in ArtifactKind.entries) {
          fold(Names.artifact(seed, culture, kind, 0L))
          fold(Names.artifact(seed, culture, kind, other))
        }
      }
      for (culture in listOf(-1) + CULTURES) {
        for (kind in RegionKind.entries) {
          fold(Names.region(seed, culture, kind.form))
          fold(Names.region(seed, culture, kind.form, epithet = true))
        }
      }
      for (kind in CultKind.entries) {
        fold(Names.cult(seed, kind, null, null))
        fold(Names.cult(seed, kind, Names.deity(other, 0, Domain.entries[index % Domain.entries.size]), null))
        for (beast in BeastKind.entries) fold(Names.cult(seed, kind, null, beast))
      }
      for (boss in WorldBoss.entries) fold(Names.monster(seed, boss))
      val of = Names.place(other, index % CULTURES.size)
      for (form in SITE_FORMS + RegionKind.entries.map { it.form }) fold(Names.site(seed, of, form))
    }
    return digest
  }

  private companion object {
    const val SWEEP_SEED = 0x4E414D45L
    const val SEEDS = 400
    val CULTURES = Culture.ALL.indices.toList()

    /** The forms `Names.siteForm` gives. */
    val SITE_FORMS = listOf(
      "ruin", "ash", "battlefield", "barrow", "monument", "mine", "abbey", "fort", "light", "hoard", "wound",
      "sanctuary", "seal", "lair", "cairn", "ward", "circle", "shrine"
    )

    // Moved when "X Field" became "X Battlefield". Nothing else was renamed.
    const val ENGLISH_DIGEST = 6_649_358_260_579_913_279L
  }
}
