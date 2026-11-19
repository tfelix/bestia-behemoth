package net.bestia.zone.questgen

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.questgen.seed.NewsSeeds
import net.bestia.zone.questgen.seed.QuestSeed
import net.bestia.zone.townsfolk.persistence.Rumour
import net.bestia.zone.townsfolk.rumour.RumourKind
import net.bestia.zone.townsfolk.rumour.RumourRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NewsSeedsTest {

  private val ecs = testWorld()
  private val view = mockk<WorldView> {
    every { read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(ecs) }
  }
  private val heard = HashMap<Int, MutableList<Rumour>>()
  private val rumours = mockk<RumourRegistry> {
    every { settlementsWithNews() } answers { heard.keys.toList() }
    every { heardBy(any()) } answers { heard[firstArg()].orEmpty() }
  }

  private val seeds = NewsSeeds(view, rumours, mockk(), mockk(), QuestgenConfig())

  @Test
  fun `big news is carried to the nearest town that has not heard it`() {
    val told = hears(HALE, RumourKind.TOWN_FELL)

    val seed = seedsOn(day = 10.0).single()

    assertEquals(QuestSeed.Kind.NEWS to Actor(ActorType.SETTLEMENT, DUNMERE), seed.kind to seed.subject)
    assertEquals(listOf(Actor(ActorType.SETTLEMENT, HALE)), seed.others)
    assertEquals(QuestSeed.News(told.id, RumourKind.TOWN_FELL), seed.news)
  }

  @Test
  fun `news goes on from the town that is nearest to the next one`() {
    hears(HALE, RumourKind.TOWN_FELL)
    val nearer = hears(DUNMERE, RumourKind.TOWN_FELL)

    val seed = seedsOn(day = 10.0).single()

    assertEquals(Actor(ActorType.SETTLEMENT, FARHOLM), seed.subject)
    assertEquals(nearer.id, seed.news?.rumourId, "Dunmere is the last town before Farholm")
  }

  @Test
  fun `news every town in reach has heard needs nobody`() {
    hears(HALE, RumourKind.BOSS_SLAIN)
    hears(DUNMERE, RumourKind.BOSS_SLAIN)
    hears(FARHOLM, RumourKind.BOSS_SLAIN)

    assertEquals(emptyList(), seedsOn(day = 10.0))
  }

  @Test
  fun `a misdeed, small news and old news are not carried`() {
    hears(HALE, RumourKind.MISDEED_SEEN)
    hears(HALE, RumourKind.BOSS_SLAIN, slots = "small", strength = 0.2)
    hears(HALE, RumourKind.BOSS_SLAIN, slots = "old", expiresOnDay = 5.0)

    assertEquals(emptyList(), seedsOn(day = 10.0))
  }

  @Test
  fun `a town out of reach is not asked for`() {
    hears(FARHOLM, RumourKind.TOWN_FELL)

    assertEquals(listOf(DUNMERE), seedsOn(day = 10.0).map { it.subject.index }, "Hale is 70 km from Farholm")
  }

  private fun seedsOn(day: Double): List<QuestSeed> {
    return seeds.seedsAmong(TOWNS, day)
  }

  private fun hears(town: Int, kind: RumourKind, slots: String = "", strength: Double = 1.0, expiresOnDay: Double = 30.0): Rumour {
    val rumour = Rumour(nextId++, town, kind, slots, postedOnDay = 1.0, expiresOnDay = expiresOnDay, strength = strength)
    heard.getOrPut(town) { ArrayList() }.add(rumour)

    return rumour
  }

  private var nextId = 1L

  private companion object {
    const val HALE = 1
    const val DUNMERE = 2
    const val FARHOLM = 3

    // On one road: Hale, 30 km on Dunmere, 40 km on Farholm.
    val TOWNS = mapOf(HALE to Vec2d(0.0, 0.0), DUNMERE to Vec2d(30_000.0, 0.0), FARHOLM to Vec2d(70_000.0, 0.0))
  }
}
