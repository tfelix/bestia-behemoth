package net.bestia.zone.skill

import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import net.bestia.zone.skill.tree.MasterSkillTreeRegistry

/**
 * Whether an entity still counts as a novice: a master who has put no skill point into any tree but Novice.
 *
 * What the novice-only starter kit is gated on - see [net.bestia.zone.item.persistence.Item.noviceOnly]. Taking Basic
 * Skill to 5 deliberately keeps a master a novice, because the whole Novice tree is the part of the game the
 * kit is meant to carry them through; it is the first point spent in Craftsman, Survival, Scholar or Warrior
 * that ends it.
 *
 * A player bestia is never a novice. Novicehood is a stage of the master's training, and the kit is for the
 * master alone - a bestia must not drink the master's potions or wear their starter gear.
 *
 * ### It asks the registry, not the skills
 *
 * [KnownSkills] exposes no iteration, and neither the `skill` nor the `learned_skill` table carries a tree -
 * that lives only in [MasterSkillTreeRegistry], loaded from `master_skill_tree.yml`. So the question is asked
 * the other way round, walking the (few dozen) non-Novice nodes and reading a level for each, the same
 * direction [BasicSkillGate] and `PassiveSkillScriptRegistry.bound()` already take. No database round trip,
 * which is what makes it safe to call from inside a world scope.
 */
@Service
class NoviceGate(
  private val skillTree: MasterSkillTreeRegistry
) {

  /**
   * True while [entityId] is a master holding no level in any non-Novice node.
   *
   * A master without [KnownSkills] has learned nothing and reads as a novice.
   */
  fun isNovice(world: World, entityId: EntityId): Boolean {
    if (!world.has(entityId, Master::class)) {
      return false
    }

    val knownSkills = world.get(entityId, KnownSkills::class)
      ?: return true

    return skillTree.nonNoviceNodes().none { knownSkills.levelOf(it.skillId) > 0 }
  }
}
