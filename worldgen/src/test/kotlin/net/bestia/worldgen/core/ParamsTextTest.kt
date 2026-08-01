package net.bestia.worldgen.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The params file format.
 *
 * Every test here is a statement about a failure that has to be *loud*, because the whole reason this format is
 * hand-written rather than `java.util.Properties` is that the JDK's version is quiet about exactly these: a
 * duplicate key wins last, an unknown key is ignored, and a line number is not kept at all. A quiet params file
 * is worse than none - it generates a world nobody asked for and reports success.
 */
class ParamsTextTest {

  private fun parse(vararg lines: String) =
    ParamsText.parse((listOf("params-format = 1") + lines).joinToString("\n"), "test.params")

  @Test
  fun `a value is read, and an absent key leaves the default alone`() {
    val text = parse("tectonics.oceanicShare = 0.61")
    val source = text.scope("tectonics")

    assertEquals(0.61, source.double("oceanicShare", 0.45))
    assertEquals(0.50, source.double("targetLandFraction", 0.50), "an absent key must not move the default")
  }

  @Test
  fun `comments, blank lines and stray whitespace are ignored`() {
    val text = parse(
      "",
      "# a comment on its own line",
      "   tectonics.oceanicShare   =   0.61   # and a trailing one",
      "\t"
    )

    assertEquals(0.61, text.scope("tectonics").double("oceanicShare", 0.45))
  }

  @Test
  fun `a duplicate key names both lines`() {
    val error = assertFailsWith<ParamsTextException> {
      parse("tectonics.oceanicShare = 0.61", "tectonics.oceanicShare = 0.62")
    }

    // Lines 2 and 3, because the format line is line 1.
    assertTrue("2" in error.message!! && "3" in error.message!!, "both lines must be named: ${error.message}")
  }

  @Test
  fun `the format line is required, and required first`() {
    assertFailsWith<ParamsTextException> {
      ParamsText.parse("tectonics.oceanicShare = 0.61", "test.params")
    }
    assertFailsWith<ParamsTextException> {
      ParamsText.parse("tectonics.oceanicShare = 0.61\nparams-format = 1", "test.params")
    }
    assertFailsWith<ParamsTextException> {
      ParamsText.parse("params-format = 2", "test.params")
    }
  }

  @Test
  fun `an unknown key is reported with its line and the nearest key that exists`() {
    val text = parse("tectonics.oceanicShore = 0.61")
    text.scope("tectonics").double("oceanicShare", 0.45)

    val error = assertFailsWith<ParamsTextException> { text.checkAllConsumed() }

    assertTrue("line 2" in error.message!!, "the line must be named: ${error.message}")
    assertTrue("tectonics.oceanicShare" in error.message!!, "the suggestion must appear: ${error.message}")
  }

  @Test
  fun `every unknown key is reported, not the first`() {
    // A misspelling usually comes with its neighbours - somebody copied a block out of a sibling's file - and
    // one error per run means one run per typo.
    val text = parse("tectonics.nonsense = 1.0", "tectonics.alsoNonsense = 2.0")
    text.scope("tectonics").double("oceanicShare", 0.45)

    val error = assertFailsWith<ParamsTextException> { text.checkAllConsumed() }

    assertTrue("nonsense" in error.message!! && "alsoNonsense" in error.message!!, error.message!!)
    assertTrue("2 key(s)" in error.message!!, error.message!!)
  }

  @Test
  fun `a key whose loader is not written yet says so rather than suggesting a misspelling`() {
    val text = parse("strata.foldAmplitude = 200")

    val error = assertFailsWith<ParamsTextException> { text.checkAllConsumed(setOf("strata")) }

    assertTrue("cannot be set from a file yet" in error.message!!, error.message!!)
  }

  @Test
  fun `consumed-key tracking is what makes an unknown key detectable at all`() {
    // The negative control for the mechanism, not just for the message: a key nobody asked for is the *only*
    // definition of unknown this format has, so a file read by nobody must fail.
    val text = parse("tectonics.oceanicShare = 0.61")

    assertFailsWith<ParamsTextException> { text.checkAllConsumed() }

    text.scope("tectonics").double("oceanicShare", 0.45)
    text.checkAllConsumed()
  }

  @Test
  fun `default means derive, and an empty value never does`() {
    val text = parse("tectonics.plateSpacing = default", "tectonics.hotspotSpacing =")
    val source = text.scope("tectonics")

    assertNull(source.doubleOrDerived("plateSpacing", 700_000.0))

    // The failure this guards: a truncated file or a bad shell expansion produces an empty value, and if that
    // meant "derive it" the world would silently change shape.
    val error = assertFailsWith<ParamsTextException> { source.doubleOrDerived("hotspotSpacing", 90_000.0) }
    assertTrue("no value" in error.message!!, error.message!!)
  }

  @Test
  fun `default is refused for a tunable that is not derived`() {
    val text = parse("tectonics.oceanicShare = default")

    val error = assertFailsWith<ParamsTextException> { text.scope("tectonics").double("oceanicShare", 0.45) }

    assertTrue("not a derived tunable" in error.message!!, error.message!!)
  }

  @Test
  fun `an underscore in a number is refused by name`() {
    // 75_000.0 is what somebody copying a default out of the source writes. Telling them it is Kotlin syntax
            // is more use than telling them it is not a number.
    val text = parse("erosion.basins.spacing = 75_000.0")

    val error = assertFailsWith<ParamsTextException> {
      text.scope("erosion").scope("basins").double("spacing", 75_000.0)
    }

    assertTrue("underscores" in error.message!!, error.message!!)
  }

  @Test
  fun `a non-finite number is refused, which is the only complete guard against a NaN digest`() {
    // NaN has many bit patterns and the digest is `toRawBits`, so a NaN reaching a params class could make
    // `pipelineVersion` differ between two runs that read the same file. The per-class `require` blocks catch
    // it only where their bounds happen to exclude it; this catches every field.
    for (value in listOf("NaN", "Infinity", "-Infinity")) {
      val text = parse("climate.equatorTemperature = $value")
      assertFailsWith<ParamsTextException>("'$value' must be refused") {
        text.scope("climate").double("equatorTemperature", 28.0)
      }
    }
  }

  @Test
  fun `ints, booleans, lists and enums`() {
    val text = parse(
      "tectonics.hotspotChainLength = 9",
      "droplets.enabled = true",
      "town.streets.rings = 0.30, 0.60, 0.90",
      "habitability.culture = seafaring"
    )

    assertEquals(9, text.scope("tectonics").int("hotspotChainLength", 7))
    assertEquals(true, text.scope("droplets").boolean("enabled", false))
    assertEquals(
      listOf(0.30, 0.60, 0.90),
      text.scope("town").scope("streets").doubleList("rings", listOf(0.28, 0.55, 0.82))
    )
    assertEquals(
      Season.SEAFARING,
      text.scope("habitability").enum("culture", Season.AGRARIAN, Season.entries.toTypedArray()),
      "an enum is matched by name, case-insensitively - a hand-written file should not have to shout"
    )
  }

  @Test
  fun `a malformed line, key or value names the line`() {
    for (line in listOf("tectonics.oceanicShare 0.61", "not a key = 1.0", "tectonics..oceanicShare = 1.0")) {
      val error = assertFailsWith<ParamsTextException>("'$line' must be refused") { parse(line) }
      assertTrue("line 2" in error.message!!, "${error.message} for '$line'")
    }

    val text = parse("tectonics.oceanicShare = quite a lot")
    val error = assertFailsWith<ParamsTextException> { text.scope("tectonics").double("oceanicShare", 0.45) }
    assertTrue("line 2" in error.message!!, error.message!!)
  }

  @Test
  fun `a list with an empty element is refused`() {
    // `0.28, , 0.82` is a half-finished edit, and an empty element silently dropped would move a town's rings
    // inward by one.
    val text = parse("town.streets.rings = 0.28, , 0.82")

    assertFailsWith<ParamsTextException> {
      text.scope("town").scope("streets").doubleList("rings", listOf(0.28, 0.55, 0.82))
    }
  }

  /** A local enum, so the test does not pin itself to a catalogue that is free to change. */
  private enum class Season { AGRARIAN, SEAFARING }
}
