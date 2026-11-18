package net.bestia.zone.chronicle

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.ArtifactKind
import net.bestia.worldgen.core.ArtifactRecord
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.SettlementRecord
import net.bestia.worldgen.core.ThreadKind
import net.bestia.worldgen.core.ThreadRecord
import net.bestia.zone.chronicle.persistence.WorldFact
import net.bestia.zone.chronicle.persistence.WorldFactRepository
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * A blade lies lost on a battlefield and its home temple wants it back. An oath of vengeance is open too, and
 * nothing here may touch it.
 */
class LivingChronicleTest {

  private val happened = ArrayList<WorldFact>()

  private val facts = mockk<WorldFactRepository> {
    every { findAllBySubjectTypeOrderById(ActorType.ARTIFACT) } answers { happened.toList() }
    every { findAllBySubjectTypeAndSubjectIndexOrderById(ActorType.ARTIFACT, any()) } answers {
      happened.filter { it.subjectIndex == secondArg<Int>() }
    }
  }

  private val worldService = mockk<WorldService> {
    every { generated.world.chronicle } returns CHRONICLE
  }

  private val living = LivingChronicle(worldService, facts, mockk(), mockk(relaxed = true))

  @Test
  fun `before anything happens in play the history stands as it was generated`() {
    assertEquals(ArtifactWhereabouts.AtSite(BATTLEFIELD), living.whereaboutsOf(BLADE))
    assertEquals(
      setOf(ThreadKind.ARTIFACT_LOST, ThreadKind.ARTIFACT_AWAY, ThreadKind.VENGEANCE_UNFULFILLED),
      living.openThreads().map { it.kind }.toSet(),
    )
  }

  @Test
  fun `a master who takes the blade ends the search, and the temple still wants it back`() {
    val taken = happened(EventKind.ARTIFACT_TAKEN, listOf(BLADE_ACTOR), byMasterId = 9, byMasterName = "Ayla")

    assertEquals(ArtifactWhereabouts.WithMaster(9, "Ayla"), living.whereaboutsOf(BLADE))
    assertEquals(
      listOf(
        LivingThread(ThreadKind.VENGEANCE_UNFULFILLED, OATH.actors, sinceEvent = OATH.since),
        LivingThread(ThreadKind.ARTIFACT_AWAY, listOf(BLADE_ACTOR, HOME_ACTOR), sinceFact = taken.id),
      ),
      living.openThreads(),
    )
  }

  @Test
  fun `the blade enshrined in its home temple closes both of its stories`() {
    happened(EventKind.ARTIFACT_TAKEN, listOf(BLADE_ACTOR), byMasterId = 9, byMasterName = "Ayla")
    happened(EventKind.ARTIFACT_ENSHRINED, listOf(BLADE_ACTOR, HOME_ACTOR), byMasterId = 9, byMasterName = "Ayla")

    assertEquals(ArtifactWhereabouts.KeptAt(HOME), living.whereaboutsOf(BLADE))
    assertEquals(listOf(ThreadKind.VENGEANCE_UNFULFILLED), living.openThreads().map { it.kind })
  }

  @Test
  fun `enshrined in another town it is found, but still away from home`() {
    happened(EventKind.ARTIFACT_ENSHRINED, listOf(BLADE_ACTOR, Actor(ActorType.SETTLEMENT, OTHER_TOWN)))

    assertEquals(ArtifactWhereabouts.KeptAt(OTHER_TOWN), living.whereaboutsOf(BLADE))
    assertEquals(
      setOf(ThreadKind.ARTIFACT_AWAY, ThreadKind.VENGEANCE_UNFULFILLED),
      living.openThreads().map { it.kind }.toSet(),
    )
  }

  private fun happened(kind: EventKind, actors: List<Actor>, byMasterId: Long? = null, byMasterName: String? = null): WorldFact {
    return WorldFact(kind, actors, whereX = null, whereY = null, byMasterId, byMasterName, onDay = 3.0).also { happened += it }
  }

  private companion object {
    const val BLADE = 0
    const val BATTLEFIELD = 4
    const val HOME = 0
    const val OTHER_TOWN = 1
    val BLADE_ACTOR = Actor(ActorType.ARTIFACT, BLADE)
    val HOME_ACTOR = Actor(ActorType.SETTLEMENT, HOME)

    val OATH = ThreadRecord(ThreadKind.VENGEANCE_UNFULFILLED, listOf(Actor(ActorType.FIGURE, 1), Actor(ActorType.FIGURE, 2)), since = 40)

    val CHRONICLE = Chronicle(
      startYear = 0,
      presentYear = 1000,
      events = emptyList(),
      civs = emptyList(),
      figures = emptyList(),
      artifacts = listOf(
        ArtifactRecord(
          BLADE, nameSeed = 1, ArtifactKind.BLADE, forgedYear = 200, forgedBy = 1, material = "iron", forgedAtNameSeed = 0,
          restingSite = BATTLEFIELD, provenance = listOf(30), homeTemple = HOME,
        )
      ),
      sites = emptyList(),
      settlements = listOf(standingTown(HOME), standingTown(OTHER_TOWN)),
      prunedEvents = 0,
      threads = listOf(
        ThreadRecord(ThreadKind.ARTIFACT_LOST, listOf(BLADE_ACTOR, Actor(ActorType.SITE, BATTLEFIELD)), since = 30),
        ThreadRecord(ThreadKind.ARTIFACT_AWAY, listOf(BLADE_ACTOR, HOME_ACTOR), since = 30),
        OATH,
      ),
    )

    fun standingTown(index: Int): SettlementRecord {
      return SettlementRecord(
        index, foundedYear = 100, abandonedYear = 0, population = 300, wealth = 1.0, ownerCiv = 0, foundingCiv = 0,
        timesSacked = 0, wallYear = 0, wallPopulation = 0, nameSeed = 7, nameCulture = 0, formerNames = emptyList(),
        ruinCause = null, sites = emptyList(),
      )
    }
  }
}
