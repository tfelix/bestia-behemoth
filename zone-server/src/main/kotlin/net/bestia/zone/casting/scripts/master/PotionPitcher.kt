package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.PotionThrowing
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Potion Pitcher (`skills.yml` id 175): throws a potion from the caster's bag onto an ally, where it works as if the
 * ally drank it, `+10%/lv` stronger. `args[0]` is the potion's item id.
 */
@Component
class PotionPitcher(
  losService: LineOfSightService,
  private val potions: PotionThrowing? = null,
) : BasicMagicSkillStrategy(losService) {

  override fun checkTarget(
    world: World,
    casterId: EntityId,
    targetEntityId: EntityId?,
    skillLevel: Int,
    args: List<Long>,
  ): OpError? {
    val itemId = args.firstOrNull() ?: return OpError.SKILL_INVALID_CHOICE
    val targetId = targetEntityId ?: return OpError.SKILL_NEEDS_ALLY
    val throwing = potions ?: return OpError.REQUEST_REFUSED

    return throwing.refusal(world, casterId, targetId, itemId)
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val targetId = ctx.targetEntityId ?: return false
    val itemId = ctx.args.firstOrNull() ?: return false

    return super.isCastPossible(ctx) && ctx.world.potionThrowRefusal(targetId, itemId) == null
  }

  override fun execute(ctx: SkillContext): Damage? {
    val targetId = ctx.targetEntityId ?: return null
    val itemId = ctx.args.firstOrNull() ?: return null

    ctx.world.throwPotion(targetId, itemId, strengthPercent(ctx.skillLevel))

    return null
  }

  companion object {
    private const val MAX_LEVEL = 5

    /** How strong a thrown potion is, in percent of one drunk. */
    fun strengthPercent(skillLevel: Int): Int {
      return 100 + 10 * skillLevel.coerceIn(1, MAX_LEVEL)
    }
  }
}
