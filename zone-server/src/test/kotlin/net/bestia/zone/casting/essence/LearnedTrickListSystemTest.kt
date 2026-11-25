package net.bestia.zone.casting.essence

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.ecs.status.LearnedTricksChanged
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Account
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

class LearnedTrickListSystemTest {

  private val world = testWorld()
  private val skillLists = mockk<ActiveSkillListPush>(relaxed = true)
  private val system = LearnedTrickListSystem(skillLists)

  @Test
  fun `a player entity whose tricks changed has its skill list sent once`() {
    val bestia = world.createEntity { id ->
      add(id, Account(ACCOUNT_ID))
      add(id, LearnedTricksChanged)
    }

    system.update(world, 0.05f)
    system.update(world, 0.05f)

    verify(exactly = 1) { skillLists.pushIfActive(ACCOUNT_ID, bestia) }
    assertFalse(world.has(bestia, LearnedTricksChanged::class))
  }

  private companion object {
    const val ACCOUNT_ID = 7L
  }
}
