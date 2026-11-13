package net.bestia.zone.item.mining

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.voxel.CarveBrush
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.stream.ChunkStreamInbox
import net.bestia.zone.item.mining.ecs.BlastFuseSystem
import org.springframework.stereotype.Component

/**
 * Blasting charges set on the ground and burning down. When one goes off, the rock around it breaks and its ore
 * scatters; the hurt is the skill's area effect.
 */
@Component
class Blasts(
  private val oreInBrush: OreInBrush,
  private val miningSkill: MiningSkill,
  private val inbox: ChunkStreamInbox,
) {

  private class Charge(val accountId: Long, val casterId: EntityId, val ball: CarveBrush, var fuseSeconds: Float)

  /** Locked: a cast sets a charge in `CastingSystem`, which may run beside [BlastFuseSystem]'s wave. */
  private val charges = ArrayList<Charge>()

  /** Why [casterId] may not blast [radiusMetres] around [at], or null when they may. */
  fun refusal(world: World, casterId: EntityId, at: Vec3L, radiusMetres: Int): OpError? {
    val miningLevel = miningSkill.levelOf(world.get(casterId, KnownSkills::class))
    val ore = oreInBrush.scan(ballAt(at, radiusMetres))

    return if (oreInBrush.anyAbove(ore, MiningSkill.maxResourceLevelAt(miningLevel))) OpError.MINE_ORE_TOO_HARD else null
  }

  @Synchronized
  fun set(accountId: Long, casterId: EntityId, at: Vec3L, radiusMetres: Int) {
    charges += Charge(accountId, casterId, ballAt(at, radiusMetres), FUSE_SECONDS)
  }

  /** Burns every fuse down by [deltaTime] and sets off the ones that reach zero. */
  @Synchronized
  fun burn(deltaTime: Float) {
    val burnt = charges.iterator()
    while (burnt.hasNext()) {
      val charge = burnt.next()
      charge.fuseSeconds -= deltaTime
      if (charge.fuseSeconds > 0f) continue

      inbox.offerCarve(ChunkStreamInbox.Carve(charge.accountId, charge.ball, yieldTo = charge.casterId, scatters = true))
      burnt.remove()
    }
  }

  companion object {
    /** As long as the skill's area takes to strike, so the rock and the foes are hit together. */
    const val FUSE_SECONDS = 3f

    /** Centred on the voxel's centre, so a blast aimed at a voxel is symmetric about it. */
    fun ballAt(at: Vec3L, radiusMetres: Int): CarveBrush {
      return CarveBrush.sphere(at.x + HALF_VOXEL, at.y + HALF_VOXEL, at.z + HALF_VOXEL, radiusMetres.toDouble())
    }

    private const val HALF_VOXEL = 0.5
  }
}
