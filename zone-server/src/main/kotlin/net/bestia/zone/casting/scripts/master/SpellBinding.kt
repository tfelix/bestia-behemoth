package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SpellBindings
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Spell Binding (`skills.yml` id 70): binds the attack on a spell scroll to a spot or an item, with a trigger and a
 * filter. Releasing an own binding is a free action. The args belong to the spellbinding slice, see [SpellBindings].
 */
@Component
class SpellBinding(
  losService: LineOfSightService,
  private val bindings: SpellBindings? = null,
) : BasicMagicSkillStrategy(losService) {

  override fun freeAction(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): Boolean {
    return bindings?.release(world, casterId, args) == true
  }

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    val bindings = bindings ?: return OpError.REQUEST_REFUSED

    return bindings.bindRefusal(world, casterId, skillLevel, args)
  }

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.bindSpell(ctx.args, ctx.aimedAt)?.let(ctx.world::tellCaster)

    return null
  }
}
