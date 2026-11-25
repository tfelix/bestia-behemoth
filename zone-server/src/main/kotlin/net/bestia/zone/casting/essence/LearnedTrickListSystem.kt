package net.bestia.zone.casting.essence

import net.bestia.zone.battle.ecs.status.LearnedTricksChanged
import net.bestia.zone.casting.plagiarism.SkillListPush
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/** Sends a master its skill list again once its gear grants other Learned Tricks, so the Borrowed tab follows the gear. */
@SpringComponent
class LearnedTrickListSystem(
  private val skillLists: SkillListPush,
) : System {

  // Any later phase sees the marker of this tick's recalc; upkeep keeps it out of the busy waves.
  override val phase = Phase.UPKEEP
  override val reads: ComponentClassSet = setOf(LearnedTricksChanged::class)
  override val writes: ComponentClassSet = setOf(LearnedTricksChanged::class)

  override fun update(world: World, deltaTime: Float) {
    val changed = mutableListOf<EntityId>()
    world.query(LearnedTricksChanged::class).each { id -> changed.add(id) }
    if (changed.isEmpty()) return

    changed.forEach { world.remove(it, LearnedTricksChanged::class) }
    // Between ticks: the list reads the skills, the copy and the gear, which this system does not declare.
    world.post { changed.filter { isAlive(it) }.forEach { skillLists.push(this, it) } }
  }
}
