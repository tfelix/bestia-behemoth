package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.dialog.DialogArg
import net.bestia.zone.dialog.DialogId
import net.bestia.zone.dialog.DialogService
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.battle.effects.StatusEffects
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.lang.Double.POSITIVE_INFINITY

/**
 * Greets a player the first time their master reaches the world, exactly once for the life of that master.
 *
 * The "exactly once" is what the effect is for. [net.bestia.zone.account.master.MasterFactory] seeds this
 * marker into durable storage when the master row is created; [net.bestia.zone.account.master.MasterEntitySpawner]
 * replays whatever is stored on every spawn, and [apply] deletes the marker as soon as it has sent the dialog.
 * The next persist writes the emptied list, so no later login finds anything to replay.
 *
 * It never expires on its own and is never synced to the client - a master created but not selected for a
 * year still gets greeted on its first login.
 *
 * Note that [apply] is the *status value recalc* hook and runs on every recalc, not once per application
 * ([net.bestia.zone.battle.StatusEffectService.applyEffect] never calls it). Self-removal below is what
 * makes a single dialog out of that; a dedicated `onApplied` lifecycle on [StatusEffectScript] would be
 * the cleaner home for one-shot behaviour.
 */
@Component
class MasterIntroMarker(
  private val dialogService: DialogService
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
    val accountId = world.get(entityId, Account::class)?.accountId
      ?: return
    val master = world.get(entityId, Master::class)
      ?: return

    // Removed before sending, and the removal decides whether to send at all: a recalc that finds the
    // marker already gone must stay silent.
    val wasPresent = world.get(entityId, StatusEffects::class)
      ?.removeEffect(StatusEffectId.MASTER_INTRO_MARKER.id)

    if (wasPresent != true) {
      return
    }

    dialogService.send(
      accountId,
      DialogId.MASTER_INTRO,
      mapOf(
        // Has to match `args` of MASTER_INTRO in dialogs.yml exactly - DialogService rejects the send
        // otherwise, and the rejection would only surface as a swallowed exception on the tick thread.
        "masterName" to DialogArg.Text(master.name),
      )
    )
  }
}
