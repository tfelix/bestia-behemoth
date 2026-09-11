package net.bestia.zone.ai.rumour

import net.bestia.zone.ai.knowledge.Knowledge
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * That a rumour survives the round trip through a column, and fades the way it is meant to.
 *
 * The encoding is the part worth pinning. A name and a token are both strings on the way out and mean
 * opposite things - one is looked up in the translation file and one must never be - so a decoder that
 * lost the tag would still round-trip a string, still render something plausible, and put an invented
 * proper noun through `tr()` forever.
 */
class RumourTest {

  @Test
  fun `every slot kind comes back as the kind it went in as`() {
    val slots = mapOf(
      "place" to Knowledge.Slot.Name("Karth"),
      "beast" to Knowledge.Slot.Token("BEAST_DIREWOLF"),
      "count" to Knowledge.Slot.Number(3),
    )

    val decoded = Rumour.Slots.decode(Rumour.Slots.encode(slots))

    assertEquals(slots, decoded)
  }

  @Test
  fun `a name that looks like a token is still a name`() {
    // The failure this guards is silent: "AGO_DAYS" as a person's name would be looked up and come back
    // either blank or as somebody else's sentence.
    val slots = mapOf("who" to Knowledge.Slot.Name("BEAST_DIREWOLF"))

    val decoded = Rumour.Slots.decode(Rumour.Slots.encode(slots))

    assertEquals(Knowledge.Slot.Name("BEAST_DIREWOLF"), decoded["who"])
  }

  @Test
  fun `a value containing an equals sign survives`() {
    val slots = mapOf("odd" to Knowledge.Slot.Name("a=b"))

    assertEquals(slots, Rumour.Slots.decode(Rumour.Slots.encode(slots)))
  }

  @Test
  fun `no slots encodes and decodes to no slots`() {
    assertTrue(Rumour.Slots.decode(Rumour.Slots.encode(emptyMap())).isEmpty())
  }

  @Test
  fun `news fades to nothing exactly at its expiry`() {
    val rumour = rumour(postedOnDay = 10.0, expiresOnDay = 30.0, strength = 1.0)

    val fresh = rumour.importanceOn(10.0)
    val half = rumour.importanceOn(20.0)

    assertEquals(RumourKind.BOSS_SLAIN.baseImportance, fresh)
    assertTrue(half in (fresh / 2 - 1)..(fresh / 2 + 1), "halfway through its life it should be worth half")
    assertEquals(0, rumour.importanceOn(30.0))
  }

  @Test
  fun `a small thing is never worth as much as a large one`() {
    val minor = rumour(strength = 0.2).importanceOn(10.0)
    val major = rumour(strength = 1.0).importanceOn(10.0)

    assertTrue(minor < major, "strength must scale what the news is worth, got $minor and $major")
  }

  @Test
  fun `expiry is the day it stops, not the day after`() {
    val rumour = rumour(postedOnDay = 10.0, expiresOnDay = 30.0)

    assertFalse(rumour.hasExpired(29.9))
    assertTrue(rumour.hasExpired(30.0))
  }

  /** A zero-length life would divide by zero rather than simply being over. */
  @Test
  fun `news that expires the instant it is posted is worth nothing`() {
    assertEquals(0, rumour(postedOnDay = 10.0, expiresOnDay = 10.0).importanceOn(10.0))
  }

  private fun rumour(
    postedOnDay: Double = 10.0,
    expiresOnDay: Double = 30.0,
    strength: Double = 1.0,
  ): Rumour {
    return Rumour(
      id = 1,
      settlement = 0,
      kind = RumourKind.BOSS_SLAIN,
      slots = "",
      postedOnDay = postedOnDay,
      expiresOnDay = expiresOnDay,
      strength = strength,
    )
  }
}
