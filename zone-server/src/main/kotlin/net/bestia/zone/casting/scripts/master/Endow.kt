package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Endow (`skills.yml` id 73): an ally's weapon strikes as fire, water, wind or earth, picked in `args[0]` as the
 * element's ordinal, with `+5%/lv` attack for `60 s/lv`. The Blue Mana Crystal is the skill's reagent.
 *
 * The endow binds to the weapon in hand, so it stays with that weapon. A creature without one carries it on its body
 * as [StatusEffectId.MASTER_ENDOWED].
 */
@Component
class Endow(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    return if (elementOf(args) == null) OpError.SKILL_INVALID_CHOICE else null
  }

  override fun isCastPossible(ctx: SkillContext): Boolean = ctx.battle is EntityBattleContext && super.isCastPossible(ctx)

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    val element = elementOf(ctx.args) ?: return null

    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)

    if (ctx.world.endowWeapon(target, element, level)) {
      // A weapon holds one element: the endow replaces an anointing, as MasterEndowed.onApply does, and an endow the
      // body got before it drew the weapon.
      ctx.world.removeEffect(target, StatusEffectId.ASPERSIO.id)
      ctx.world.removeEffect(target, StatusEffectId.MASTER_ENDOWED.id)
    } else {
      ctx.world.applyStatusEffect(target, StatusEffectId.MASTER_ENDOWED.id, level = level, amount = element.ordinal)
    }

    return null
  }

  companion object {
    val ELEMENTS = listOf(Element.FIRE, Element.WATER, Element.WIND, Element.EARTH)

    /** The element a cast picked, or null for a number that is none of the four. */
    fun elementOf(args: List<Long>): Element? {
      val ordinal = args.firstOrNull() ?: return null
      return ELEMENTS.firstOrNull { it.ordinal.toLong() == ordinal }
    }

    private const val MAX_LEVEL = 3
  }
}
