package net.bestia.zone.item.mining

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.voxel.CarveBrush
import net.bestia.zone.battle.GearWear
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.ToolKind
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.mining.ecs.MiningSwing
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.stream.ChunkStreamInbox
import org.springframework.stereotype.Service
import kotlin.math.abs

/**
 * Mining by hand: one swing breaks a ball of rock and pays its ore to the miner. Tick thread only, like
 * [OreInBrush].
 */
@Service
class PickaxeMining(
  private val oreInBrush: OreInBrush,
  private val miningSkill: MiningSkill,
  private val inbox: ChunkStreamInbox,
  private val gearWear: GearWear,
  private val outMessageProcessor: OutMessageProcessor,
) {

  /** What [finish] reads on the tick, wear included; [begin] runs in the click's handler. */
  val reads: ComponentClassSet = setOf(CombatStats::class, StatusEffects::class, Master::class, OwnedBestia::class)

  /** Starts a swing at the rock around [at], the voxel the miner clicked. */
  fun begin(world: World, entityId: EntityId, accountId: Long, at: Vec3L) {
    val position = world.get(entityId, Position::class)?.toVec3L() ?: return
    if (abs(position.z - at.z) > VERTICAL_REACH) {
      return refuse(accountId, OpError.DIG_TOO_FAR)
    }

    val ball = ballAt(at)
    val ore = oreInBrush.scan(ball)
    val miningLevel = miningSkill.levelOf(world.get(entityId, KnownSkills::class))
    if (oreInBrush.anyAbove(ore, MiningSkill.maxResourceLevelAt(miningLevel))) {
      return refuse(accountId, OpError.MINE_ORE_TOO_HARD)
    }

    val seconds = (SECONDS_PER_BALL + SECONDS_PER_ORE * ore.size) * MiningSkill.timeFactorAt(miningLevel)
    world.add(entityId, MiningSwing(accountId, ball, seconds.toFloat()))
  }

  /** Breaks the ball the swing was aimed at. The ore reaches the bag through `OreHandOut`. */
  fun finish(world: World, entityId: EntityId, swing: MiningSwing) {
    if (!holdsPickaxe(world, entityId)) return

    inbox.offerCarve(ChunkStreamInbox.Carve(swing.accountId, swing.ball, yieldTo = entityId))
    gearWear.wearWeapon(world, entityId, WEAR_PER_SWING)
  }

  private fun holdsPickaxe(world: World, entityId: EntityId): Boolean {
    return world.get(entityId, CombatStats::class)?.values?.heldTool == ToolKind.MINING
  }

  private fun refuse(accountId: Long, code: OpError) {
    outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(code))
  }

  companion object {
    /** The narrowest bore the client can draw, so every swing breaks a ball this wide. */
    const val BALL_RADIUS = CarveBrush.MIN_RADIUS

    const val SECONDS_PER_BALL = 10.0
    const val SECONDS_PER_ORE = 20.0
    const val WEAR_PER_SWING = 1

    /** How far above or below the miner they can swing, in voxels. `DigService` checks the reach across. */
    const val VERTICAL_REACH = 4

    /** Centred on the voxel's centre, so a ball aimed at a voxel is symmetric about it. */
    fun ballAt(at: Vec3L): CarveBrush {
      return CarveBrush.sphere(at.x + HALF_VOXEL, at.y + HALF_VOXEL, at.z + HALF_VOXEL, BALL_RADIUS)
    }

    private const val HALF_VOXEL = 0.5
  }
}
