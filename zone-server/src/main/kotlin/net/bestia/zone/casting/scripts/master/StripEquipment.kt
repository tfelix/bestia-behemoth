package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.item.equip.SlotLock
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.util.Random

/**
 * Strip Equipment (`skills.yml` id 119): a `10% + 10%/lv` roll to knock a random worn piece off a foe, whose slot then
 * stays empty for `5 s/lv` (see `StrippedGear`). Fails outright against Equipment Coating. A body with no gear to take,
 * like a wild bestia, is weakened instead, as Ragnarok Online's strips do to monsters.
 */
@Component
class StripEquipment(
  losService: LineOfSightService,
  private val random: Random = ThreadLocalRandomSource,
) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)

    if (ctx.world.hasEffect(target, StatusEffectId.EQUIPMENT_COATING.id)) {
      ctx.world.tellCaster(OpError.STRIP_GEAR_COATED)
      return null
    }
    if (random.nextInt(100) >= chancePercent(level)) return null

    val worn = ctx.world.strippableGearOf(target)
    when {
      worn.isNotEmpty() -> strip(ctx, target, worn[random.nextInt(worn.size)], level)
      // A master with nothing on has nothing to lose.
      ctx.world.masterIdOf(target) == null -> weaken(ctx, target, level)
    }

    return null
  }

  /** A dual wielder loses both weapons, whichever hand the roll picked. */
  private fun strip(ctx: SkillContext, target: EntityId, piece: SlotLock, level: Int) {
    val pieces = if (piece in HANDS && ctx.world.isDualWielding(target)) HANDS else listOf(piece)

    for (lock in pieces) {
      ctx.world.strip(target, lock.slot)
      ctx.world.applyStatusEffect(target, lock.status.id, level)
    }
  }

  private fun weaken(ctx: SkillContext, target: EntityId, level: Int) {
    val lock = if (random.nextBoolean()) SlotLock.WEAPON else SlotLock.ARMOR

    ctx.world.applyStatusEffect(target, lock.status.id, level, amount = WEAKEN_PERCENT)
  }

  companion object {
    private const val MAX_LEVEL = 5

    /** A gearless body hits or guards this much worse while its "slot" is blocked. */
    const val WEAKEN_PERCENT = 25

    private val HANDS = listOf(SlotLock.WEAPON, SlotLock.SHIELD)

    fun chancePercent(level: Int): Int {
      return 10 + 10 * level.coerceIn(1, MAX_LEVEL)
    }
  }
}
