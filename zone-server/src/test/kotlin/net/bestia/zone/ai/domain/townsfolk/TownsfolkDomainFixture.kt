package net.bestia.zone.ai.domain.townsfolk

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.core.action.ActionResolver
import net.bestia.zone.navigation.TestNavigation

/**
 * Collaborators the townsfolk action templates need in order to be built.
 *
 * Only movement, and the real service over flat ground because it is cheap. A planning test asserts which
 * actions the planner chains rather than ticking their trees, so these have to exist rather than work.
 */
object TownsfolkDomainFixture {

  fun resolver(actionIds: List<String> = TownsfolkDomain.actionIds.toList()): ActionResolver {
    return TownsfolkDomain.resolver(
      actionIds,
      TownsfolkDomain.Collaborators(Locomotion(TestNavigation.service()))
    )
  }
}
