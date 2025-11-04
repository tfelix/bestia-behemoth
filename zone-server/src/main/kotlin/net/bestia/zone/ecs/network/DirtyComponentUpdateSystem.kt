package net.bestia.zone.ecs.network

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.ActivePlayerAOIService
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.player.Account
import net.bestia.zone.ecs.player.ActivePlayer
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.Entity
import net.bestia.zone.ecs.IteratingSystem
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.ZoneServer
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.processor.OutMessageProcessor
import org.reflections.Reflections
import org.springframework.stereotype.Component
import net.bestia.zone.ecs.Component as EcsComponent
import kotlin.reflect.KClass

/**
 * Responsible for filtering every entity which needs to be sent over the network as fast as possible.
 * These are entities with animations or position updates for example.
 * We also use this step to update different systems like the area of interest services.
 */
@Component
class DirtyComponentUpdateSystem(
  private val aoiService: EntityAOIService,
  private val playerAOIService: ActivePlayerAOIService,
  private val outMessageProcessor: OutMessageProcessor
) : IteratingSystem() {
  override val requiredComponents = setOf(
    Position::class,
    IsDirty::class
  )

  // Cache all Dirtyable component classes discovered via reflection at initialization
  private val dirtyableComponentClasses: Set<KClass<out Dirtyable>>

  init {
    val reflections = Reflections("net.bestia.zone.ecs")
    dirtyableComponentClasses = reflections.getSubTypesOf(Dirtyable::class.java)
      .filter { EcsComponent::class.java.isAssignableFrom(it) }
      .map { it.kotlin }
      .toSet()

    LOG.info { "Discovered ${dirtyableComponentClasses.size} Dirtyable component classes: ${dirtyableComponentClasses.map { it.simpleName }}" }
  }

  override fun update(
    deltaTime: Float,
    entity: Entity,
    zone: ZoneServer
  ) {
    val posComp = entity.getOrThrow(Position::class)
    val position = posComp.toVec3L()

    val isEntityActivePlayer = entity.has(ActivePlayer::class)

    if (posComp.isDirty()) {
      aoiService.setEntityPosition(entity.id, position)

      if (isEntityActivePlayer) {
        updatePlayerAOI(entity, position)
      }
    }

    val allDirtyComponents = collectAllDirtyComponents(entity)

    // Group components by broadcast type
    val publicComponents = mutableListOf<Dirtyable>()
    val privateComponents = mutableListOf<Dirtyable>()

    for (component in allDirtyComponents) {
      when (component.broadcastType()) {
        Dirtyable.BroadcastType.PUBLIC -> publicComponents.add(component)
        Dirtyable.BroadcastType.ONLY_OWNER -> {
          // Special case: Health should be PUBLIC for non-player entities. Maybe this must be
          // refactored to be more specific e.g. like member of party. The system probably needs
          // and upgrade then.
          if (component is Health && !entity.has(Account::class)) {
            publicComponents.add(component)
          } else {
            privateComponents.add(component)
          }
        }
      }
    }

    // Broadcast public components to all in range
    if (publicComponents.isNotEmpty()) {
      val publicMessages = publicComponents.map { component ->
        component.clearDirty()
        component.toEntityMessage(entity.id)
      }

      zone.queueExternalJob {
        outMessageProcessor.sendToAllPlayersInRange(position, publicMessages)
      }
    }

    // Send private components only to the entity owner
    if (privateComponents.isNotEmpty()) {
      val ownedByAccountId = entity.get(Account::class)?.accountId

      if (ownedByAccountId != null) {
        val privateMessages = privateComponents.map { component ->
          component.clearDirty()
          component.toEntityMessage(entity.id)
        }

        zone.queueExternalJob {
          outMessageProcessor.sendToPlayer(ownedByAccountId, privateMessages)
        }
      }
    }

    entity.remove(IsDirty::class)
  }

  /**
   * Collects all dirtyable components from an entity using the cached list
   * of Dirtyable component classes discovered at initialization.
   */
  private fun collectAllDirtyComponents(entity: Entity): List<Dirtyable> {
    return dirtyableComponentClasses.mapNotNull { componentClass ->
      @Suppress("UNCHECKED_CAST")
      entity.get(componentClass as KClass<out EcsComponent>) as? Dirtyable
    }.filter { it.isDirty() }
  }

  private fun updatePlayerAOI(entity: Entity, position: Vec3L) {
    val account = entity.get(Account::class)
    val isActivePlayer = entity.has(ActivePlayer::class)

    if (account == null || !isActivePlayer) {
      return
    }

    LOG.trace { "Updating entity ${entity.id} (account: ${account.accountId}) AOI to $position" }

    playerAOIService.setEntityPosition(account.accountId, position)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
