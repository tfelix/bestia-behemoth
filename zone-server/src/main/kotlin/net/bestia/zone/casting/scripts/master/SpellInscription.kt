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
 * Spell Inscription (`skills.yml` id 69): copies the attack `args[0]` off one of the Sage's own bestias onto a new
 * spell scroll. The bestia keeps the attack. The blank scroll is the skill's reagent, so a failure spends it too,
 * and a failure breaks the bestia's mind half of the time.
 */
@Component
class SpellInscription(
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
    val skillId = args.firstOrNull() ?: return OpError.SKILL_INVALID_CHOICE
    val bestiaId = targetEntityId ?: return OpError.SPELL_NOT_OWN_BESTIA
    val scrolls = spellScrolls ?: return OpError.REQUEST_REFUSED

    return scrolls.inscriptionRefusal(world, casterId, bestiaId, skillId)
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val bestiaId = ctx.targetEntityId ?: return false
    val skillId = ctx.args.firstOrNull() ?: return false

    return super.isCastPossible(ctx) && ctx.world.spellInscriptionRefusal(bestiaId, skillId) == null
  }

  override fun execute(ctx: SkillContext): Damage? {
    val bestiaId = ctx.targetEntityId ?: return null
    val skillId = ctx.args.firstOrNull() ?: return null
    val spell = ctx.world.bestiaAttack(bestiaId, skillId) ?: return null

    val mind = ctx.battle.attacker.statusValues
    val chance = SpellScrollOdds.inscriptionPercent(spell.attackLevel, ctx.skillLevel, mind.intelligence, mind.willpower)
    when {
      random.nextInt(100) < chance -> ctx.world.inscribeSpell(spell)
      random.nextInt(100) < SpellScrollOdds.MIND_BREAK_PERCENT -> ctx.world.breakMind(bestiaId)
      else -> ctx.world.tellCaster(OpError.SPELL_INSCRIPTION_FAILED)
    }

    return null
  }
}
