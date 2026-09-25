package net.bestia.zone.account.master.skill

import net.bestia.zone.ecs.battle.skill.KnownSkills
import org.springframework.stereotype.Service

/**
 * Whether a wearer still counts as a novice: one who has put no skill point into any tree but Novice.
 *
 * What the novice-only starter kit is gated on - see [net.bestia.zone.item.Item.noviceOnly]. Taking Basic
 * Skill to 5 deliberately keeps a master a novice, because the whole Novice tree is the part of the game the
 * kit is meant to carry them through; it is the first point spent in Craftsman, Survival, Scholar or Warrior
 * that ends it.
 *
 * ### It asks the registry, not the skills
 *
 * [KnownSkills] exposes no iteration, and neither the `skill` nor the `learned_skill` table carries a tree -
 * that lives only in [MasterSkillTreeRegistry], loaded from `master_skill_tree.yml`. So the question is asked
 * the other way round, walking the (few dozen) non-Novice nodes and reading a level for each, the same
 * direction [BasicSkillGate] and `PassiveSkillScriptRegistry.bound()` already take. No database round trip,
 * which is what makes it safe to call from inside a world lock.
 *
 * A player bestia reads as a novice, correctly: its item-taught skills are catalogued at 1000+ and have no
 * tree node at all, so none of them can end a novicehood it was never really in.
 */
@Service
class NoviceGate(
  private val skillTree: MasterSkillTreeRegistry
) {

  /**
   * True while [knownSkills] holds no level in any non-Novice node.
   *
   * Null - an entity with no such component, or none alive - reads as a novice. This is a ceiling rather
   * than a floor, so the permissive direction is the safe one here: the opposite of the level rule, where
   * `EquipmentService` treats an unknown wearer as unqualified.
   */
  fun isNovice(knownSkills: KnownSkills?): Boolean {
    if (knownSkills == null) {
      return true
    }

    return skillTree.nonNoviceNodes().none { knownSkills.levelOf(it.skillId) > 0 }
  }
}
