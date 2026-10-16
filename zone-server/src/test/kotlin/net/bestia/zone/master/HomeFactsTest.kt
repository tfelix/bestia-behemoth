package net.bestia.zone.master

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.SettlementRecord
import net.bestia.zone.cartography.render.ClientStrings
import net.bestia.zone.dialog.DialogArg
import net.bestia.zone.townsfolk.conversation.Line
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeFactsTest {

  @Test
  fun `a walled town sacked twice says so, with its years`() {
    val lines = HomeFacts.historyOf(record(foundedYear = 214, wallYear = 530, timesSacked = 2))

    assertEquals(
      listOf(
        Line(HomeFacts.FOUNDED, mapOf("year" to DialogArg.Number(214))),
        Line(HomeFacts.WALLED, mapOf("year" to DialogArg.Number(530))),
        Line(HomeFacts.SACKED_MANY, mapOf("times" to DialogArg.Number(2))),
      ),
      lines,
    )
  }

  @Test
  fun `an unwalled town sacked once has no count to fill in`() {
    val lines = HomeFacts.historyOf(record(foundedYear = 388, wallYear = 0, timesSacked = 1))

    assertEquals(listOf(HomeFacts.FOUNDED, HomeFacts.SACKED_ONCE), lines.map { it.key })
  }

  @Test
  fun `a town history never founded tells nothing`() {
    assertEquals(emptyList(), HomeFacts.historyOf(record(foundedYear = 0, wallYear = 0, timesSacked = 0)))
  }

  @Test
  fun `the client can translate every key the server sends`() {
    val keys = ClientStrings.keys()
    val sent = HomeFacts.HISTORY_KEYS + Biome.entries.map(HomeFacts::biomeToken)

    val missing = sent.filterNot { it in keys }
    assertTrue(missing.isEmpty(), "Add $missing to ${ClientStrings.GENERAL_CSV}")
  }

  private fun record(foundedYear: Int, wallYear: Int, timesSacked: Int) = SettlementRecord(
    index = 0,
    foundedYear = foundedYear,
    abandonedYear = 0,
    population = 500,
    wealth = 0.5,
    ownerCiv = 0,
    foundingCiv = 0,
    timesSacked = timesSacked,
    wallYear = wallYear,
    wallPopulation = 0,
    nameSeed = 1L,
    oldNameSeed = 0L,
    ruinCause = null,
    sites = emptyList(),
  )
}
