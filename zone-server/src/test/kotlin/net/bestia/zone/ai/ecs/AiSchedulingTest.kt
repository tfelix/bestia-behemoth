package net.bestia.zone.ai.ecs

import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The AI pipeline's ordering has to be enforced by the scheduler, not merely happen to hold.
 *
 * `SystemScheduler` decides what may run concurrently purely from each system's declared `reads`/`writes`, and
 * places non-conflicting systems in the *same* wave. All three AI stages previously declared the brain
 * component under `reads` while mutating it on every pass, so by declaration they conflicted with nothing and
 * landed in one wave together. Sense -> think -> act then held only because a single wave is executed in
 * registration order with parallel systems switched off; turning parallelism on would have run all three
 * concurrently over the same mutable state, and silently dropped the ordering the pipeline is built on.
 *
 * Declaring `AiAgent` as *written* is what restores the guarantee, and this test is what keeps it: it fails if
 * anyone widens a read/write set in a way that lets two AI stages share a wave again.
 */
class AiSchedulingTest {

  @Test
  fun `each AI stage gets its own wave, in pipeline order`() {
    val ai = AiPipelineFixture()

    // Perception, drives, think, act and movement each conflict with the previous one, so the scheduler is
    // forced to serialise them rather than being free to interleave.
    assertEquals(
      ai.systems.size,
      ai.world.waveCount,
      "every AI stage should be forced into its own wave; got ${ai.world.waveCount} " +
        "wave(s) for ${ai.systems.size} systems",
    )
  }

  @Test
  fun `the AI stages still conflict when registered in any order`() {
    // Order-independence matters because Spring supplies these beans by @Order, and a future reordering must
    // not be able to collapse them into one wave.
    val ai = AiPipelineFixture()
    val reversed = testWorld(systems = ai.systems.reversed())

    assertEquals(ai.systems.size, reversed.waveCount)
  }

  /**
   * The level-of-detail marker must not be able to move a stage into another wave.
   *
   * It is safe only because `SystemScheduler.conflicts` looks at *writes*: the marker is attached before any
   * system sees the entity and nothing ever writes it, so declaring it as read adds no conflict edge. A
   * future change that started writing it would silently repartition the AI pipeline, and the wave
   * assertions above would be the only thing to notice - so this pins the premise they rely on.
   */
  @Test
  fun `the throttle marker is read by the AI stages and written by none`() {
    val ai = AiPipelineFixture()

    val readers = ai.systems.count { AiThrottleable::class in it.reads }
    assertEquals(3, readers, "perception, senses and think should all read the throttle marker")

    for (system in ai.systems) {
      assertEquals(
        false,
        AiThrottleable::class in system.writes,
        "${system::class.simpleName} writes AiThrottleable, which would repartition the scheduler waves"
      )
    }
  }

  @Test
  fun `the act stage declares the components its behaviour trees actually write`() {
    val ai = AiPipelineFixture()
    val act = ai.systems.filterIsInstance<AiActSystem>().single()

    // Locomotion writes Path and MacroRoute; casting a skill reaches Damage, Health and Mana. Leaving these
    // undeclared is what put the AI in the same wave as the movement and combat systems that consume them.
    val declared = act.writes.map { it.simpleName }.toSet()
    assertTrue(declared.containsAll(setOf("AiAgent", "Path", "MacroRoute", "Damage", "Health", "Mana")),
      "act system under-declares its writes: $declared")
  }
}
