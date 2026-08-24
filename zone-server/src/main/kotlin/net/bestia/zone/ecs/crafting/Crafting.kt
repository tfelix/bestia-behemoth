package net.bestia.zone.ecs.crafting

import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.battle.skill.CastingComponentSMSG
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.Countdown
import net.bestia.zone.ecs.core.Removable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * Marks an entity as working on one craft, carrying everything needed to resolve it when
 * [remainingSeconds] hits zero. [CraftingSystem] drives the countdown and hands the finished craft to
 * [net.bestia.zone.crafting.CraftingService].
 *
 * ### It sends a [CastingComponentSMSG]
 *
 * Deliberately, and it is the reason there is no crafting-progress message. A craft and a cast are the
 * same thing to a player - a bar that fills and then either resolves or is interrupted - so reusing the
 * message means the client draws both with the code it already has. The two channels are mutually
 * exclusive: [net.bestia.zone.ecs.battle.skill.CastCancelService] drops both together, so a bar can
 * only ever belong to one of them. It is throttled the same way too - see [Countdown].
 *
 * ### Its inputs are not spent yet
 *
 * Nothing is consumed until the craft resolves, which is why cancelling refunds nothing and needs to:
 * there is nothing to give back. The trade-off is that the inputs have to be re-checked at the end, and
 * a craft whose materials were dropped, used or traded away in the meantime fails as
 * `CRAFT_MISSING_MATERIALS` rather than resolving against a bag that no longer holds them.
 */
class Crafting(
  val recipeId: Long,

  /** The item instance being worked on, or 0 for a recipe that makes something instead. */
  val targetUniqueId: Long,

  val totalSeconds: Float,
  remainingSeconds: Float = totalSeconds,
) : Countdown(remainingSeconds), Removable {

  // A craft bar reports "still busy" rather than feeding a decision, so it beats at half the rate a
  // cast bar does. The accumulator itself is Countdown's.
  override val syncIntervalSeconds = RESYNC_INTERVAL

  init {
    require(totalSeconds > 0f) { "Crafting requires a positive totalSeconds, got $totalSeconds" }
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG =
    CastingComponentSMSG(
      entityId = entityId,
      remainingSeconds = remainingSeconds.coerceAtLeast(0f),
      totalSeconds = totalSeconds,
      removed = removed
    )

  // Bystanders see the bar too, so a smith at a forge is visibly busy - the same choice Casting makes.
  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange

  companion object {
    private const val RESYNC_INTERVAL = 2f
  }
}
