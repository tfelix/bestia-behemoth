package net.bestia.zone.casting.essence

import net.bestia.zone.battle.ecs.status.LearnedTricksChanged
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/**
 * Sends a player the skill list again once the gear of its master or Bestia grants other Learned Tricks, so the Borrowed
 * tab follows the gear.
 */
@SpringComponent
class LearnedTrickListSystem(
  private val skillLists: ActiveSkillListPush,
) : System {

  // Any later phase sees the marker of this tick's recalc; upkeep keeps it out of the busy waves.
  override val phase = Phase.UPKEEP
  override val reads: ComponentClassSet = setOf(LearnedTricksChanged::class, Account::class)
  override val writes: ComponentClassSet = setOf(LearnedTricksChanged::class)

  override fun update(world: World, deltaTime: Float) {
    val changed = mutableListOf<Pair<EntityId, AccountId>>()
    world.query(LearnedTricksChanged::class, Account::class).each { id -> changed.add(id to get<Account>().accountId) }
    if (changed.isEmpty()) return

    changed.forEach { (id, accountId) ->
      world.remove(id, LearnedTricksChanged::class)
      skillLists.pushIfActive(accountId, id)
    }
  }
}
