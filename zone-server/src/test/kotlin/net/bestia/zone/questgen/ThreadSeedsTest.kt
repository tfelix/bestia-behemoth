package net.bestia.zone.questgen

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.ThreadKind
import net.bestia.zone.chronicle.LivingChronicle
import net.bestia.zone.chronicle.LivingThread
import net.bestia.zone.questgen.seed.QuestSeed
import net.bestia.zone.questgen.seed.ThreadSeeds
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ThreadSeedsTest {

  private val chronicle = mockk<LivingChronicle>()

  @Test
  fun `a lost artifact is a seed for a race, other open stories are not seeds yet`() {
    val blade = Actor(ActorType.ARTIFACT, 3)
    val battlefield = Actor(ActorType.SITE, 8)
    every { chronicle.openThreads() } returns listOf(
      LivingThread(ThreadKind.ARTIFACT_LOST, listOf(blade, battlefield), sinceEvent = 30),
      LivingThread(ThreadKind.ARTIFACT_AWAY, listOf(blade, Actor(ActorType.SETTLEMENT, 0)), sinceEvent = 30),
      LivingThread(ThreadKind.BOSS_SEALED, listOf(Actor(ActorType.MONSTER, 1)), sinceEvent = 12),
    )

    val seeds = ThreadSeeds(chronicle).seeds()

    assertEquals(listOf(QuestSeed(QuestSeed.Kind.LOST_RELIC, blade, listOf(battlefield), QuestSeed.Uniqueness.UNIQUE)), seeds)
  }
}
