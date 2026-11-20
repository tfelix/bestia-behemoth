package net.bestia.zone.stronghold

import net.bestia.zone.ecs.core.World
import net.bestia.zone.guild.skill.GuildStrongholds
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component
import kotlin.math.hypot

/** An entity is inside its guild's Stronghold while it stands on the grounds of an Empyrite the guild holds. */
@Component
class StrongholdGrounds(
  private val strongholds: StrongholdRegistry,
  private val seeds: EmpyriteSeedRegistry,
  private val worldService: WorldService,
  private val config: StrongholdConfig,
) : GuildStrongholds {

  override fun isInOwnStronghold(world: World, entityId: EntityId, guildId: Long): Boolean {
    val at = world.get(entityId, Position::class) ?: return false
    val voxel = worldService.config.voxelSize
    val held = strongholds.heldBy(guildId).filter { it.state == State.STANDING }.map { it.rank }.toSet()

    return seeds.awake()
      .filter { it.rank in held }
      .any { hypot(it.x - at.x * voxel, it.y - at.y * voxel) <= config.groundsRadiusMetres }
  }
}
