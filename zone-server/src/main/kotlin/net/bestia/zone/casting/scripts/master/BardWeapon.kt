package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** What a Bard's skill is played with. Without it in hand the button refuses the skill and a cast fizzles. */
enum class BardWeapon(
  private val type: WeaponType,
  private val refusal: OpError,
) {
  WHIP(WeaponType.WHIP, OpError.SKILL_NEEDS_WHIP);

  /** For [net.bestia.zone.casting.SkillStrategy.checkCastStart]. */
  fun refusalFor(world: World, casterId: EntityId): OpError? {
    val held = world.get(casterId, CombatStats::class)?.values?.heldWeaponType

    return if (held == type) null else refusal
  }

  fun isHeld(ctx: SkillContext): Boolean {
    return ctx.world.heldWeaponTypeOf(ctx.casterId) == type
  }
}
