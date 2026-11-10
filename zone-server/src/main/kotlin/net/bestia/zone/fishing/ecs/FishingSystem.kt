package net.bestia.zone.fishing.ecs

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.ecs.attack.AttackTarget
import net.bestia.zone.battle.ecs.skill.Casting
import net.bestia.zone.battle.ecs.skill.Channel
import net.bestia.zone.battle.ecs.skill.Crafting
import net.bestia.zone.casting.ecs.CastingSystem
import net.bestia.zone.casting.ecs.ChannelSystem
import net.bestia.zone.crafting.ecs.CraftingSystem
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.fishing.FishingCatalogue
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.respawn.ecs.RespawnSystem
import net.bestia.zone.teleport.TeleportSystem
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/**
 * Waits each line out to its bite, lets the bite pass, and reels the line in once the angler does anything else.
 */
@SpringComponent
class FishingSystem(
  private val catalogue: FishingCatalogue,
  private val outMessageProcessor: OutMessageProcessor,
  private val nanoTime: () -> Long = java.lang.System::nanoTime,
) : System {
  override val phase = Phase.ACTIONS

  // Last in its phase, so a cast, craft, respawn or teleport this tick reels the line in on the same tick.
  override val after = setOf(
    CastingSystem::class, ChannelSystem::class, CraftingSystem::class, RespawnSystem::class, TeleportSystem::class,
  )

  override val reads: ComponentClassSet = setOf(
    Position::class, Equipment::class, Account::class,
    Casting::class, Channel::class, Crafting::class, AttackTarget::class,
  )
  override val writes: ComponentClassSet = setOf(Fishing::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Fishing::class, Position::class).each { id ->
      val fishing = get<Fishing>()

      if (isBusyElsewhere(world, id, fishing, get<Position>())) {
        world.remove(id, Fishing::class)
        return@each
      }

      fishing.countDown(deltaTime)
      if (!fishing.isDue) {
        return@each
      }

      if (fishing.isBiting) {
        world.remove(id, Fishing::class)
        tellGotAway(world, id)
      } else {
        fishing.bite(catalogue.rules.strikeWindowSeconds, nanoTime())
      }
    }
  }

  private fun isBusyElsewhere(world: World, id: EntityId, fishing: Fishing, position: Position): Boolean {
    val rodUniqueId = world.get(id, Equipment::class)?.get(EquipmentSlot.RIGHT_HAND)?.uniqueId

    return position.toVec3L() != fishing.castFrom ||
      rodUniqueId != fishing.rodUniqueId ||
      world.has(id, Casting::class) ||
      world.has(id, Channel::class) ||
      world.has(id, Crafting::class) ||
      world.has(id, AttackTarget::class)
  }

  private fun tellGotAway(world: World, id: EntityId) {
    val accountId = world.get(id, Account::class)?.accountId ?: return
    outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(OpError.FISHING_GOT_AWAY))
  }
}
