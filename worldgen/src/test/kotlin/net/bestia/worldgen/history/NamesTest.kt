package net.bestia.worldgen.history

import net.bestia.worldgen.civ.Culture
import net.bestia.worldgen.core.ArtifactKind
import net.bestia.worldgen.core.BeastKind
import net.bestia.worldgen.core.CultKind
import net.bestia.worldgen.core.Domain
import net.bestia.worldgen.core.FigureRole
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.WorldBoss
import net.bestia.worldgen.place.RegionKind
import kotlin.test.Test
import kotlin.test.assertEquals

class NamesTest {

  @Test
  fun `every English name is what it was`() {
    // A change to how names are built must not rename an existing world. Re-pin only for a rename you mean.
    assertEquals(ENGLISH_DIGEST, englishDigest())
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

    /** The forms `HistorySim.siteName` passes. */
    val SITE_FORMS = listOf(
      "ruin", "ash", "field", "barrow", "monument", "mine", "abbey", "fort", "light", "hoard", "wound",
      "sanctuary", "seal", "lair", "cairn", "ward", "circle", "shrine"
    )

    const val ENGLISH_DIGEST = 8_990_054_608_266_844_108L
  }
}
