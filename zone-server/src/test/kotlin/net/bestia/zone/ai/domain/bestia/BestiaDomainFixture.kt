package net.bestia.zone.ai.domain.bestia

import io.mockk.mockk
import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.core.action.ActionResolver
import net.bestia.zone.battle.skill.SkillExecutionService
import net.bestia.zone.navigation.TestNavigation

/**
 * Collaborators the bestia action templates need in order to be built.
 *
 * A *planning* test never ticks a behaviour tree — it asserts about which actions the planner chains and
 * what that does to memory — so the tree's dependencies only have to exist, not work. Navigation gets the
 * real service over flat ground because it is cheap, and the skill service is mocked because building a
 * real one means five more beans for something no planning assertion touches.
 */
object BestiaDomainFixture {

  fun locomotion(): Locomotion = Locomotion(TestNavigation.service())

  fun skills(): SkillExecutionService = mockk(relaxed = true)

  fun resolver(
    actionIds: List<String>,
    attacks: List<AttackDefinition> = emptyList(),
  ): ActionResolver = BestiaDomain.resolver(actionIds, locomotion(), skills(), attacks)
}
