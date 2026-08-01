package net.bestia.worldgen.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ParamsDigestTest {

  @Test
  fun `the same fields always give the same digest`() {
    assertEquals(digest(0.45, 7), digest(0.45, 7))
  }

  @Test
  fun `changing any value changes the digest`() {
    val base = digest(0.45, 7)

    assertNotEquals(base, digest(0.46, 7))
    assertNotEquals(base, digest(0.45, 8))
  }

  @Test
  fun `the order fields are put in does not matter`() {
    // So that reordering a constructor's parameters is not a false invalidation. The same argument
    // WorldGenPipeline makes by folding dependencies in name order.
    val declared = ParamsDigest().put("a", 1.0).put("b", 2.0).value
    val reversed = ParamsDigest().put("b", 2.0).put("a", 1.0).value

    assertEquals(declared, reversed)
  }

  @Test
  fun `swapping two fields values is not the same as leaving them alone`() {
    // The reason names are folded and not only values: minWarYears and maxWarYears hold the same *kind* of
    // number, so a fold over values alone could not tell the two configurations apart.
    val ordered = ParamsDigest().put("min", 1.0).put("max", 2.0).value
    val swapped = ParamsDigest().put("min", 2.0).put("max", 1.0).value

    assertNotEquals(ordered, swapped)
  }

  @Test
  fun `a field digested twice is refused`() {
    // The failure this catches is a copy-paste: `put("min", minWarYears); put("min", maxWarYears)`. The set of
    // names would still match toString(), so the oracle cannot see it and this must.
    val thrown = assertFailsWith<IllegalArgumentException> {
      ParamsDigest().put("spacing", 1.0).put("spacing", 2.0)
    }

    assertEquals(true, thrown.message!!.contains("spacing"))
  }

  @Test
  fun `an absent nullable is not any value it could take`() {
    // `plateSpacing = null` means "derive it from the world", which is a different generator from one that
    // names a number - so the absence needs its own slot rather than a sentinel, because NaN payloads make
    // every 64-bit pattern reachable from some toRawBits().
    val derived = ParamsDigest().putOrDerived("plateSpacing", null).value

    assertNotEquals(derived, ParamsDigest().putOrDerived("plateSpacing", 0.0).value)
    assertNotEquals(derived, ParamsDigest().putOrDerived("plateSpacing", Double.NaN).value)
    assertNotEquals(derived, ParamsDigest().putOrDerived("plateSpacing", -0.0).value)
  }

  @Test
  fun `zero and negative zero are different tunings`() {
    // They compare equal as doubles and have different bits. Whichever the generator branches on, the digest
    // has to see the difference, because `if (x < 0)` does not.
    assertNotEquals(
      ParamsDigest().put("shift", 0.0).value,
      ParamsDigest().put("shift", -0.0).value
    )
  }

  @Test
  fun `a list folds its length as well as its values`() {
    // Two ring radii and three are different towns, and the shorter list must not be a prefix collision.
    val two = ParamsDigest().put("rings", listOf(0.28, 0.55)).value
    val three = ParamsDigest().put("rings", listOf(0.28, 0.55, 0.82)).value

    assertNotEquals(two, three)
    assertNotEquals(two, ParamsDigest().put("rings", listOf(0.55, 0.28)).value)
    assertEquals(two, ParamsDigest().put("rings", listOf(0.28, 0.55)).value)
  }

  @Test
  fun `an enum folds its name rather than its ordinal`() {
    // A reorder of an enum whose ordinal is not stored is not a change to the world; a rename is. Ordinals
    // that *are* load bearing get folded in list order by their catalogue's own digest instead.
    assertEquals(
      ParamsDigest().put("layout", Layout.ORGANIC).value,
      ParamsDigest().put("layout", Layout.ORGANIC).value
    )
    assertNotEquals(
      ParamsDigest().put("layout", Layout.ORGANIC).value,
      ParamsDigest().put("layout", Layout.GRID).value
    )
    // Same name, same slot, whichever enum it came from: the digest is over the configuration's meaning.
    assertEquals(
      ParamsDigest().put("layout", Layout.GRID).value,
      ParamsDigest().put("layout", OtherLayout.GRID).value
    )
  }

  @Test
  fun `a nested digest changes the outer one`() {
    val outer = ParamsDigest().put("a", 1.0).nested("basins", 0x1234L).value

    assertNotEquals(outer, ParamsDigest().put("a", 1.0).nested("basins", 0x1235L).value)
  }

  @Test
  fun `adding a field moves the digest even when its value is zero`() {
    // GenRng.hash seeds with the argument count, which is what makes this true. Without it a new field
    // defaulting to 0.0 would be invisible, and a new tunable is exactly when the digest matters most.
    assertNotEquals(
      ParamsDigest().put("a", 1.0).value,
      ParamsDigest().put("a", 1.0).put("b", 0.0).value
    )
  }

  @Test
  fun `names are reported in call order`() {
    assertEquals(listOf("b", "a"), ParamsDigest().put("b", 1.0).put("a", 2.0).names)
  }

  // --- the oracle itself, so it cannot rot silently ------------------------------------------------------

  @Test
  fun `the field oracle reads a data class's own field names`() {
    assertEquals(setOf("spacing", "depth", "enabled", "label"), ParamsFields.of(Sample()))
  }

  @Test
  fun `the field oracle names a nested params object without descending into it`() {
    // `basins=Sample(spacing=..., ...)` must yield `basins` and none of Sample's fields, or every nested
    // params class would make its parent's completeness check pass for the wrong reason.
    assertEquals(setOf("scale", "basins"), ParamsFields.of(Nesting()))
  }

  @Test
  fun `the field oracle matches a literal rendering`() {
    // Pinned against the exact string, so a change in how Kotlin renders a data class is a failure here
    // rather than a wrong answer in seventeen completeness checks.
    assertEquals(
      "Sample(spacing=50000.0, depth=-3400.0, enabled=false, label=demo)",
      Sample().toString()
    )
  }

  private fun digest(share: Double, chain: Int) =
    ParamsDigest().put("oceanicShare", share).put("hotspotChainLength", chain).value

  private enum class Layout { ORGANIC, GRID }

  private enum class OtherLayout { GRID }

  private data class Sample(
    val spacing: Double = 50_000.0,
    val depth: Double = -3_400.0,
    val enabled: Boolean = false,
    val label: String = "demo"
  )

  private data class Nesting(val scale: Double = 1.0, val basins: Sample = Sample())
}
