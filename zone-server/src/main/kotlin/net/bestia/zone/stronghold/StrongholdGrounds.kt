package net.bestia.zone.stronghold

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.guild.skill.GuildStrongholds
import net.bestia.zone.identity.ecs.GuildAffiliation
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.GroundClaims
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component
import kotlin.math.hypot

/**
 * The ground a Stronghold claims. An awake seed's core holds against every tool and building. Once an Empyrite is
 * planted, nobody digs on the grounds round it, only its guild builds there, and its members are inside their own
 * Stronghold.
 */
@Component
class StrongholdGrounds(
  private val strongholds: StrongholdRegistry,
  private val seeds: EmpyriteSeedRegistry,
  private val worldService: WorldService,
  private val config: StrongholdConfig,
) : GuildStrongholds, GroundClaims {

  override fun isInOwnStronghold(world: World, entityId: EntityId, guildId: Long): Boolean {
    val at = world.get(entityId, Position::class)?.toVec3L() ?: return false
    val held = strongholds.heldBy(guildId).filter { it.state == State.STANDING }.map { it.rank }.toSet()

    return seeds.awake().filter { it.rank in held }.any { metresTo(it, at) <= config.groundsRadiusMetres }
  }

  /** A dig whose ball reaches a core or a Stronghold's grounds is refused. */
  override fun digRefusal(at: Vec3L, radius: Double): OpError? {
    val reach = radius * worldService.config.voxelSize

    return when {
      coreAt(at, reach) != null -> OpError.GROUND_SEED_CORE_HOLDS
      strongholdAt(at, reach) != null -> OpError.GROUND_STRONGHOLD_NO_DIG
      else -> null
    }
  }

  override fun buildRefusal(world: World, builderId: EntityId, at: Vec3L): OpError? {
    if (coreAt(at, 0.0) != null) return OpError.BUILD_ON_SEED_CORE

    val stronghold = strongholdAt(at, 0.0) ?: return null
    val builderGuild = GuildAffiliation.membershipOf(world, builderId)?.guildId

    return if (builderGuild == stronghold.guildId) null else OpError.BUILD_ON_STRONGHOLD_GROUNDS
  }

  /** The Stronghold, planted or standing, whose grounds [at] lies on, or reaches within [reachMetres] of. */
  fun strongholdAt(at: Vec3L, reachMetres: Double = 0.0): Stronghold? {
    val seed = seeds.awake().firstOrNull { metresTo(it, at) - reachMetres <= config.groundsRadiusMetres } ?: return null

    return strongholds.on(seed.rank)
  }

  private fun coreAt(at: Vec3L, reachMetres: Double): EmpyriteSeed? {
    return seeds.awake().firstOrNull { metresTo(it, at) - reachMetres <= config.coreRadiusMetres }
  }

  /** Flat: the core is a column, and the grounds a circle, from the sky down to the bottom of the world. */
  private fun metresTo(seed: EmpyriteSeed, at: Vec3L): Double {
    val voxel = worldService.config.voxelSize

    return hypot(seed.x - at.x * voxel, seed.y - at.y * voxel)
  }
}
