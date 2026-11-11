package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SpellScrollOdds
import net.bestia.zone.casting.SpellScrolls
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.util.Random

/**
 * Spell Training (`skills.yml` id 61): teaches the attack on a spell scroll to one of the Sage's own bestias.
 * `args[0]` is the scroll's unique id. The scroll is spent whether the training takes or not.
 */
@Component
class SpellTraining(
  losService: LineOfSightService,
  private val spellScrolls: SpellScrolls? = null,
  private val random: Random = ThreadLocalRandomSource,
) : BasicMagicSkillStrategy(losService) {

  override fun checkTarget(
    world: World,
    casterId: EntityId,
    targetEntityId: EntityId?,
    skillLevel: Int,
    args: List<Long>,
  ): OpError? {
    val scrollId = args.firstOrNull() ?: return OpError.SKILL_INVALID_CHOICE
    val bestiaId = targetEntityId ?: return OpError.SPELL_NOT_OWN_BESTIA
    val scrolls = spellScrolls ?: return OpError.REQUEST_REFUSED

    return scrolls.trainingRefusal(world, casterId, bestiaId, scrollId)
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val bestiaId = ctx.targetEntityId ?: return false
    val scrollId = ctx.args.firstOrNull() ?: return false

    return super.isCastPossible(ctx) && ctx.world.spellTrainingRefusal(bestiaId, scrollId) == null
  }

  override fun execute(ctx: SkillContext): Damage? {
    val bestiaId = ctx.targetEntityId ?: return null
    val scrollId = ctx.args.firstOrNull() ?: return null
    val spell = ctx.world.takeSpellScroll(scrollId) ?: return null

    val chance = SpellScrollOdds.trainingPercent(spell.attackLevel, ctx.skillLevel)
    if (random.nextInt(100) < chance) {
      ctx.world.teachSpell(bestiaId, spell)
    } else {
      ctx.world.tellCaster(OpError.SPELL_TRAINING_FAILED)
    }

    return null
  }
}
