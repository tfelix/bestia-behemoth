package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.dialog.DialogArg
import net.bestia.zone.dialog.DialogId
import net.bestia.zone.dialog.DialogService
import net.bestia.zone.ecs.battle.effects.StatusEffects
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component
import java.lang.Double.POSITIVE_INFINITY

/**
 * Applied to every master entity as it materializes ([net.bestia.zone.account.master.MasterEntityFactory])
 * and does nothing whatsoever - it is a placeholder for the "greet the player once their master is in the world"
 * trigger, giving that work a real, already-attached, never-synced effect to hang off.
 */
@Component
class MasterIntroMarker(
  private val dialogService: DialogService,
  private val worldService: WorldService
) : StatusEffectScript {

  override val stackBehavior: StackBehavior = StackBehavior.IGNORE_IF_PRESENT

  override fun durationSeconds(level: Int): Double = POSITIVE_INFINITY

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    world.updateOrIgnore<StatusEffects>(entityId) {
      it.removeEffect()
    }

    dialogService.send(
      1L,
      DialogId.MASTER_INTRO,
      mapOf(
        "worldName" to DialogArg.Text(worldService.record.name),
      )
    )
  }
}
