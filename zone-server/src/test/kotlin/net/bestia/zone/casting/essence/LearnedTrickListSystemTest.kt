package net.bestia.zone.casting.essence

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.ecs.status.LearnedTricksChanged
import net.bestia.zone.casting.plagiarism.SkillListPush
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

class LearnedTrickListSystemTest {

  private val world = testWorld()
  private val skillLists = mockk<SkillListPush>(relaxed = true)
  private val system = LearnedTrickListSystem(skillLists)

  @Test
  fun `a master whose tricks changed is sent its skill list once`() {
    val master = world.createEntity { id -> add(id, LearnedTricksChanged) }

    system.update(world, 0.05f)
    system.update(world, 0.05f)
    world.runPostedUntil(System.nanoTime() + POSTED_NANOS)

    verify(exactly = 1) { skillLists.push(world, master) }
    assertFalse(world.has(master, LearnedTricksChanged::class))
  }

  private companion object {
    const val POSTED_NANOS = 50_000_000L
  }
}
