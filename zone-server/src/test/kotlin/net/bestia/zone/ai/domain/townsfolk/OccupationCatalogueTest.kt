package net.bestia.zone.ai.domain.townsfolk

import net.bestia.worldgen.pop.BusinessCatalogue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That the occupations and the trades the world generator builds agree, in both directions.
 *
 * The second direction is the one worth having. An occupation naming a trade that does not exist shows up
 * the first time somebody tries to spawn one; a trade with nobody to keep it shows up as a shop standing
 * in every town in the world with the lights off, which looks exactly like nothing being wrong.
 *
 * Both halves are asserted here as well as checked at boot, because a boot failure is found by whoever
 * runs the server next and a test failure by whoever caused it.
 */
class OccupationCatalogueTest {

  private val catalogue = OccupationCatalogue().apply { load() }

  @Test
  fun `the seven occupations load`() {
    assertEquals(
      listOf("beggar", "child", "farmer", "guard", "innkeeper", "labourer", "priest"),
      catalogue.ids().sorted()
    )
  }

  @Test
  fun `every trade is either kept by somebody or listed as deliberately empty`() {
    OccupationCoverage(catalogue).check()
  }

  @Test
  fun `and the two lists between them account for every trade exactly once`() {
    // Restates the boot check as arithmetic, so a trade that slipped into both lists is a failure here
    // rather than a coverage check that passes by counting it twice.
    val staffed = catalogue.all().mapNotNull { it.businessType }
    val unstaffed = catalogue.unstaffedTrades()

    assertEquals(staffed.size, staffed.toSet().size, "two occupations claim the same trade: $staffed")
    assertEquals(
      BusinessCatalogue.ALL.map { it.id }.toSet(),
      staffed.toSet() + unstaffed,
      "the trades and the two lists have drifted apart"
    )
  }

  @Test
  fun `nobody works through their own bedtime`() {
    // Refused at load, so this can only fail via a file that got past it - but the property is what the
    // refusal is for: a post open through the night makes which of sleep and work wins a priority accident.
    for (occupation in catalogue.all()) {
      val shift = occupation.shift ?: continue

      assertFalse(
        shift.overlaps(occupation.rest),
        "${occupation.id} works $shift and sleeps ${occupation.rest}"
      )
    }
  }

  @Test
  fun `somebody keeps hours the commoner does not`() {
    // The reason a resting window is per person rather than per archetype. With every occupation on the
    // default this whole seam would be untested and would rot.
    assertTrue(
      catalogue.all().any { it.rest != OccupationCatalogue.DEFAULT_REST },
      "every occupation sleeps on the commoner's hours, so nothing exercises the per-person window"
    )
  }

  @Test
  fun `a day with no work in it is expressible`() {
    // The case WorkShift has to survive rather than a gap in the file: a child has no post at all.
    val child = assertNotNull(catalogue.get("child"))

    assertEquals(null, child.shift)
    assertEquals(null, child.businessType)
  }
}
