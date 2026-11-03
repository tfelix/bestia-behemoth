package net.bestia.zone.honor

import net.bestia.zone.casting.Speech
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * A townsperson who sees a misdeed calls it out in public chat, so the players nearby know who did it long before
 * any rumour has travelled. English, like a creature's proclamation: chat lines are said, not looked up.
 */
@Component
class WitnessOutcry(
  private val speech: Speech,
) {

  fun cryOut(world: World, witness: EntityId, act: HonorAct, offenderName: String) {
    val line = lineFor(act, offenderName) ?: return

    speech.say(world, witness, line)
  }

  private fun lineFor(act: HonorAct, offenderName: String): String? {
    return when (act) {
      HonorAct.KILL_NPC, HonorAct.KILL_PLAYER, HonorAct.KILL_WEAK_OPPOSING_PLAYER -> "Murder! $offenderName killed them!"
      HonorAct.STEAL_MUNDANE, HonorAct.STEAL_SUPERIOR, HonorAct.STEAL_RARE, HonorAct.STEAL_LEGENDARY ->
        "Thief! Stop $offenderName!"
      HonorAct.DAMAGE_BUILDING, HonorAct.DISPEL_WARD_STONE, HonorAct.DESTROY_WARD_STONE ->
        "Stop that, $offenderName!"
      else -> null
    }
  }
}
