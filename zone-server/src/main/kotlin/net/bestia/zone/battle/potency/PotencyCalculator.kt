package net.bestia.zone.battle.potency

import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.BoundSpellCaster
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.skill.tree.MasterSkillTreeRegistry
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.mana.ManaConcentrationService
import org.springframework.stereotype.Service

/** Turns a cast into its [Potency], from the skill's rank, the caster and the mana where the caster stands. */
@Service
class PotencyCalculator(
  private val skillTree: MasterSkillTreeRegistry,
  private val mana: ManaConcentrationService,
) {

  fun castPotency(world: World, casterId: EntityId, skill: Skill, skillLevel: Int): Int {
    world.get(casterId, BoundSpellCaster::class)?.let { return it.potency }

    val combat = world.get(casterId, CombatStats::class)?.values
    val attributes = combat?.attributes
    val at = world.get(casterId, Position::class)?.toVec3L()

    return Potency.of(
      rank = rankOf(skill, skillLevel),
      maxRank = maxRankOf(skill),
      level = world.get(casterId, Level::class)?.level ?: 1,
      intelligence = attributes?.intelligence ?: 0,
      willpower = attributes?.willpower ?: 0,
      focus = combat?.potencyFocus ?: 0,
      place = at?.let { mana.placeAt(it) } ?: 0,
    )
  }

  /** What [targetId] defends a hostile spell with. */
  fun resolveOf(world: World, targetId: EntityId): Int {
    val combat = world.get(targetId, CombatStats::class)?.values

    return Potency.resolve(
      level = world.get(targetId, Level::class)?.level ?: 1,
      intelligence = combat?.attributes?.intelligence ?: 0,
      willpower = combat?.attributes?.willpower ?: 0,
      focus = combat?.potencyFocus ?: 0,
    )
  }

  /** A master skill's rank counts against its own maximum; a Bestia attack's level is worth three points. */
  private fun rankOf(skill: Skill, skillLevel: Int): Int {
    return if (skillTree.findBySkillId(skill.id) != null) skillLevel else Potency.BESTIA_RANK_PER_LEVEL * skillLevel
  }

  private fun maxRankOf(skill: Skill): Int = skillTree.findBySkillId(skill.id)?.maxLevel ?: Potency.MAX_RANK
}
