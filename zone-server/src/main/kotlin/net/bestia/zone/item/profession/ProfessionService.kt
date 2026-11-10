package net.bestia.zone.item.profession

import net.bestia.zone.ecs.core.World
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.tree.MasterSkillTreeRegistry
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/** Keeps a master's [Professions] in step with the points spent in each sub-tree. */
@Service
class ProfessionService(
  private val skillTree: MasterSkillTreeRegistry,
) {

  /** Re-decides [entityId]'s professions after its skills changed. Does nothing for an entity without them. */
  fun refresh(world: World, entityId: EntityId) {
    val professions = world.get(entityId, Professions::class) ?: return
    val known = world.get(entityId, KnownSkills::class) ?: return

    professions.held = ProfessionResolver.resolve(pointsBySubTree(known), professions.held)
  }

  /** What [entityId] may use, or null for an entity that professions do not limit, like a bestia. */
  fun allowanceOf(world: World, entityId: EntityId): GearAllowance? {
    return world.get(entityId, Professions::class)?.allowance
  }

  private fun pointsBySubTree(known: KnownSkills): Map<String, Int> {
    return skillTree.all()
      .filter { it.subTree != null }
      .groupBy { it.subTree!! }
      .mapValues { (_, nodes) -> nodes.sumOf { known.levelOf(it.skillId) } }
  }
}
