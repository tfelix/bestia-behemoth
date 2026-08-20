package net.bestia.zone.world.prop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.prop.PlayerStructureIdentity
import net.bestia.zone.ecs.prop.PropPose
import net.bestia.zone.world.WorldService
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Forgets a player-built station that has been knocked down.
 *
 * The counterpart to [PropDeathDivergenceSystem], and separate from it because the two record opposite things.
 * A felled tree writes a *divergence* row saying the generator's output no longer applies here; a broken forge
 * has no generator output to diverge from, so its own row is simply deleted and there is nothing left to say.
 *
 * `@Order(66)`: after `ReceivedDamageSystem` (50) has added [Dead] and before `DeathSystem` (70) destroys the
 * entity, alongside the divergence system at 65. Nothing about the two conflicts, so their relative order does
 * not matter - only that both run inside that window.
 */
@SpringComponent
@Order(66)
class PlayerStructureDeathSystem(
  private val structures: PlayerStructureRegistry,
  private val worldService: WorldService
) : System {

  override val reads: ComponentClassSet = setOf(Dead::class, PlayerStructureIdentity::class, PropPose::class)

  // Empty for the reason PropDeathDivergenceSystem's is: the only mutation is to the registry's own map, which
  // is off the ECS entirely.
  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    world.query(Dead::class, PlayerStructureIdentity::class, PropPose::class).each { _ ->
      val identity = get<PlayerStructureIdentity>()
      val position = get<PropPose>().position

      val chunkSize = worldService.config.chunkSize.toLong()
      structures.remove(
        structureId = identity.structureId,
        chunkX = Math.floorDiv(position.x, chunkSize).toInt(),
        chunkY = Math.floorDiv(position.y, chunkSize).toInt()
      )

      LOG.info { "Player structure ${identity.structureId} was destroyed at $position" }
    }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
