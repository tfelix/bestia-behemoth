package net.bestia.worldgen.resource

import net.bestia.worldgen.core.ParamsText
import net.bestia.worldgen.core.ParamsTextException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * How much of each ore a world holds, set from a params file.
 *
 * The behaviour worth testing here is not the arithmetic - it is that an override *reaches* the generator and
 * that a typo does not silently do nothing, which is the failure mode `ParamsText` exists to remove and which a
 * per-entry scope is the easiest place to reintroduce.
 */
class OreTuningTest {

  private fun tuningFrom(vararg lines: String): OreTuning {
    val text = ParamsText.parse((listOf("params-format = 1") + lines).joinToString("\n"), "test.params")
    val tuning = OreTuning().overriddenBy(text.scope("resource").scope("ore"))
    text.checkAllConsumed()

    return tuning
  }

  @Test
  fun `an absent key leaves the ore's own number alone`() {
    val tuning = tuningFrom()

    for (ore in MinableOre.entries) {
      assertEquals(ore.tonsPerThousandSqKm, tuning.abundanceOf(ore), "${ore.name} abundance")
      assertEquals(ore.spacingFactor, tuning.spacingOf(ore), "${ore.name} spacing")
      assertEquals(ore.guaranteedDeposits, tuning.floorOf(ore), "${ore.name} floor")
    }
  }

  @Test
  fun `abundance, spacing and floor are set per ore and nothing else moves`() {
    val tuning = tuningFrom(
      "resource.ore.diamond.abundance = 0.5",
      "resource.ore.ruby.spacing = 1.1",
      "resource.ore.emerald.floor = 4"
    )

    assertEquals(0.5, tuning.abundanceOf(MinableOre.DIAMOND))
    assertEquals(1.1, tuning.spacingOf(MinableOre.RUBY))
    assertEquals(4, tuning.floorOf(MinableOre.EMERALD))

    // The other knobs of each ore, and a neighbouring ore, are untouched. A scope that leaked would show here.
    assertEquals(MinableOre.DIAMOND.spacingFactor, tuning.spacingOf(MinableOre.DIAMOND))
    assertEquals(MinableOre.DIAMOND.guaranteedDeposits, tuning.floorOf(MinableOre.DIAMOND))
    assertEquals(MinableOre.RUBY.tonsPerThousandSqKm, tuning.abundanceOf(MinableOre.RUBY))
    assertEquals(MinableOre.EMERALD.spacingFactor, tuning.spacingOf(MinableOre.EMERALD))
    assertEquals(MinableOre.IRON.tonsPerThousandSqKm, tuning.abundanceOf(MinableOre.IRON))
  }

  /**
   * Zero is a legitimate floor, and the one value that must survive the "absent means default" rule.
   *
   * `overriddenBy` only records a value that *differs* from the enum's, so an ore whose default is already zero
   * would record nothing - which is right. What must not happen is the reverse: setting an ore with a floor of
   * three down to zero has to reach [OreTuning.floorOf] rather than read back as three. It is also how
   * `RawGeology` turns the whole guarantee off, so `GemDepositTest` and `VolcanicResourceTest` depend on it.
   */
  @Test
  fun `a floor of zero is an override and not an absence`() {
    val tuning = tuningFrom("resource.ore.iron.floor = 0")

    assertEquals(0, tuning.floorOf(MinableOre.IRON))
    assertEquals(MinableOre.COPPER.guaranteedDeposits, tuning.floorOf(MinableOre.COPPER))
    assertTrue(MinableOre.IRON.guaranteedDeposits > 0, "the test is vacuous if iron had no floor to begin with")
  }

  /**
   * The reason every key is asked for whether the file sets it or not.
   *
   * `ParamsText` derives its schema from what readers ask for, so an ore that is never asked about would make
   * every misspelling of its name an unknown key - which is right - but an ore that *is* asked about turns a
   * misspelling into a suggestion. The failure this guards is the opposite one: a reader that only asked for
   * the keys the file happened to set would accept `resource.ore.dimond.abundance` in silence.
   */
  @Test
  fun `a misspelled ore is refused rather than ignored`() {
    val error = assertFailsWith<ParamsTextException> {
      tuningFrom("resource.ore.dimond.abundance = 0.5")
    }

    assertTrue("dimond" in error.message!!, "the message should name the key that nothing read: ${error.message}")
  }

  @Test
  fun `a value that cannot be a quantity is refused`() {
    assertFailsWith<IllegalArgumentException> { OreTuning(abundance = mapOf(MinableOre.RUBY to 0.0)) }
    assertFailsWith<IllegalArgumentException> { OreTuning(spacing = mapOf(MinableOre.RUBY to -1.0)) }
    assertFailsWith<IllegalArgumentException> { OreTuning(floor = mapOf(MinableOre.RUBY to -1)) }
  }

  /**
   * The digest moves on an override, which is what makes a params file part of the world's identity.
   *
   * Without it a world generated with `resource.ore.diamond.spacing = 1.0` and one generated without it would
   * share a `paramsVersion`, and the chunk cache would serve one world's terrain for the other.
   */
  @Test
  fun `the digest folds the effective numbers`() {
    val plain = OreTuning()
    val tuned = tuningFrom("resource.ore.diamond.spacing = 1.0")

    // `.value`, because ParamsDigest is a builder and does not define equality - two digests of the same
    // numbers are different objects.
    assertNotEquals(plain.digest().value, tuned.digest().value)

    // And an override that restates the default is not a change, because the digest folds effective values
    // rather than the presence of a key.
    val restated = tuningFrom("resource.ore.diamond.spacing = ${MinableOre.DIAMOND.spacingFactor}")
    assertEquals(plain.digest().value, restated.digest().value)
  }

  /**
   * The small-world floor is measured against the tuned spacing, not the enum's.
   *
   * `ResourceParams.spacingShrink` divides by the coarsest ore's spacing to decide whether a world is too small
   * to sample properly. Reading that off the enum while the sampler read it off the tuning would leave the
   * rarest ore sampled more coarsely than the floor was computed for - which is the bug the floor exists to fix,
   * reintroduced by the knob meant to help.
   */
  @Test
  fun `the coarsest spacing follows an override`() {
    val coarsest = MinableOre.entries.maxOf { it.spacingFactor }
    val tuning = tuningFrom("resource.ore.copper.spacing = ${coarsest + 1.0}")

    assertEquals(coarsest + 1.0, tuning.coarsestSpacing())
    assertEquals(coarsest, OreTuning().coarsestSpacing())
  }
}
