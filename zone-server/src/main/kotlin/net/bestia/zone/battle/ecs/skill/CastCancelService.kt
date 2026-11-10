package net.bestia.zone.battle.ecs.skill

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Single entry point for aborting a running cast or craft. Removing the component is what both stops the
 * countdown and notifies the client (via the generic component-removed message), so every "the channel got
 * interrupted" path funnels through here. No-op when nothing is running.
 *
 * The two channels are separate components and one bar: [Crafting] deliberately emits the same
 * [CastingComponentSMSG] a cast does, so anything that ends one has to be able to end the other, and
 * `CraftItemHandler` / `ActivateSkillHandler` each cancel the opposite before starting their own.
 */
@Service
class CastCancelService(
  private val otherWork: List<InterruptibleWork> = emptyList(),
) {

  /** Also ends a channel and the stillness it holds its caster in: a channel is a cast that outlived its bar. */
  fun cancelCast(world: World, entityId: EntityId) {
    Casting.end(world, entityId)
    // A ritual's channel breaks the same way a skill's does.
    if (world.has(entityId, Channel::class) || world.has(entityId, RitualParticipant::class)) {
      world.remove(entityId, Channel::class)
      world.remove(entityId, RitualParticipant::class)
      if (world.get(entityId, StatusEffects::class)?.removeEffect(StatusEffectId.CHANNELING.id) == true) {
        world.add(entityId, IsStatusValueDirty)
      }
    }
  }

  /** Ends a channel and a ritual's channel but leaves a cast bar running, for a caster who may move while casting. */
  fun cancelChannel(world: World, entityId: EntityId) {
    if (world.has(entityId, Channel::class) || world.has(entityId, RitualParticipant::class)) {
      world.remove(entityId, Channel::class)
      world.remove(entityId, RitualParticipant::class)
      if (world.get(entityId, StatusEffects::class)?.removeEffect(StatusEffectId.CHANNELING.id) == true) {
        world.add(entityId, IsStatusValueDirty)
      }
    }
  }

  fun cancelCraft(world: World, entityId: EntityId) {
    world.remove(entityId, Crafting::class)
    otherWork.forEach { world.remove(entityId, it.component) }
  }

  /** Breaks off whatever the entity was busy with, for a stun or a freeze. */
  fun interrupt(world: World, entityId: EntityId) {
    cancelCast(world, entityId)
    cancelCraft(world, entityId)
  }
}
