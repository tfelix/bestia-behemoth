package net.bestia.zone.battle.attack

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.passive.scripts.PoisonResearch
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.scripts.Poison
import net.bestia.zone.battle.status.scripts.PoisonedWeapon
import net.bestia.zone.ecs.core.World
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.SkillIds
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** A weapon under Enchant Poison may poison what it lands on, harder in the hands of a Poison Research adept. */
@Component
class PoisonedBlade(
  private val statusEffects: StatusEffectService,
  private val skillIds: SkillIds,
) : SwingProc {

  private val poisonResearchId: Long? get() = skillIds[SkillId.M_POISON_RESEARCH]

  override fun onSwingLanded(world: World, attackerId: EntityId, targetId: EntityId, swing: Damage) {
    val coating = world.get(attackerId, StatusEffects::class)?.activeEffects
      ?.firstOrNull { it.definitionId == StatusEffectId.ENCHANT_POISON.id }
      ?: return
    val research = poisonResearchId?.let { world.get(attackerId, KnownSkills::class)?.levelOf(it) } ?: 0
    val drain = coating.amount.takeIf { it > 0 } ?: Poison.DEFAULT_DRAIN_PER_MILLE

    statusEffects.tryInflict(
      world, targetId, StatusEffectId.POISON, level = 1, sourceEntityId = attackerId,
      chancePercent = PoisonedWeapon.chancePercent(coating.level),
      amount = PoisonResearch.researchedDrain(drain, research),
    )
  }
}
