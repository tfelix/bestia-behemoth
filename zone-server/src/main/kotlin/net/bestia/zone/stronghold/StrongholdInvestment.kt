package net.bestia.zone.stronghold

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.skill.SkillId
import net.bestia.zone.stronghold.ecs.Empyrite
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import kotlin.random.Random

/**
 * Members invest coins at their guild's Empyrite, one step of one track per click. Tick thread only.
 */
@Service
class StrongholdInvestment(
  private val strongholds: StrongholdRegistry,
  private val seeds: EmpyriteSeedRegistry,
  private val empyrites: Empyrites,
  private val coins: StrongholdCoins,
  private val messages: OutMessageProcessor,
  private val config: StrongholdConfig,
  private val random: Random = Random.Default,
) {

  /** A member clicked their guild's Empyrite: the window opens. */
  fun open(world: World, actorId: EntityId, empyriteId: EntityId): OpError? {
    refusal(world, actorId, empyriteId)?.let { return it }
    sendWindow(world, actorId, empyriteId)

    return null
  }

  /** One step of [track], paid by the member. A step counts twice by chance with Permanent Development. */
  fun invest(world: World, actorId: EntityId, empyriteId: EntityId, track: StrongholdTrack): OpError? {
    refusal(world, actorId, empyriteId)?.let { return it }
    val stronghold = strongholdOf(world, empyriteId) ?: return OpError.REQUEST_REFUSED
    val masterId = world.get(actorId, Master::class)?.masterId ?: return OpError.REQUEST_REFUSED

    // The window offers no button at the top level, so only a stale window asks.
    val progress = stronghold.progressOf(track)
    if (progress.isMaxed) return OpError.REQUEST_REFUSED

    if (!coins.pay(world, actorId, masterId, stepCoins(progress))) return OpError.STRONGHOLD_CANNOT_AFFORD_STEP

    val steps = if (hasPermanentDevelopment(world, actorId) && random.nextDouble() < config.doubleStepChance) 2 else 1
    val advanced = progress.advanced(steps)
    strongholds.put(stronghold.withProgress(track, advanced))
    if (track == StrongholdTrack.DEFENSE && advanced.level != progress.level) empyrites.harden(world, stronghold.rank)

    sendWindow(world, actorId, empyriteId)

    return null
  }

  private fun refusal(world: World, actorId: EntityId, empyriteId: EntityId): OpError? {
    val stronghold = strongholdOf(world, empyriteId) ?: return OpError.REQUEST_REFUSED
    val membership = world.get(actorId, GuildMembership::class)
    val actorAt = world.get(actorId, Position::class)?.toVec3L()
    val empyriteAt = world.get(empyriteId, Position::class)?.toVec3L()

    return when {
      membership?.guildId != stronghold.guildId -> OpError.STRONGHOLD_NOT_YOUR_GUILDS
      actorAt == null || empyriteAt == null -> OpError.REQUEST_REFUSED
      actorAt.distance(empyriteAt) > RANGE_TILES -> OpError.STRONGHOLD_OUT_OF_RANGE
      else -> null
    }
  }

  private fun sendWindow(world: World, actorId: EntityId, empyriteId: EntityId) {
    val stronghold = strongholdOf(world, empyriteId) ?: return
    val accountId = world.get(actorId, Account::class)?.accountId ?: return
    val health = world.get(empyriteId, Health::class) ?: return
    val seedName = seeds.awake().firstOrNull { it.rank == stronghold.rank }?.name.orEmpty()
    val guildName = world.get(actorId, GuildMembership::class)?.guildName.orEmpty()
    val tracks = StrongholdTrack.entries.map { track ->
      val progress = stronghold.progressOf(track)
      StrongholdSMSG.Track(track, progress, if (progress.isMaxed) 0 else stepCoins(progress))
    }

    val window = StrongholdSMSG(
      empyriteId, seedName, guildName, health.current, health.max, tracks, hasPermanentDevelopment(world, actorId),
      config.empyriteHpPerDefense, config.empyriteDefensePerLevel
    )
    messages.sendToPlayer(accountId, window)
  }

  /** A step towards level N costs N times the base. */
  private fun stepCoins(progress: TrackProgress): Long {
    return config.stepCoinsPerLevel * (progress.level + 1)
  }

  private fun hasPermanentDevelopment(world: World, actorId: EntityId): Boolean {
    return (world.get(actorId, GuildMembership::class)?.skillLevel(SkillId.PERMANENT_DEVELOPMENT.name) ?: 0) > 0
  }

  private fun strongholdOf(world: World, empyriteId: EntityId): Stronghold? {
    val rank = world.get(empyriteId, Empyrite::class)?.rank ?: return null

    return strongholds.on(rank)
  }

  companion object {
    /** How close a member stands to invest, in tiles. The client closes the window a little further off. */
    const val RANGE_TILES = 6L
  }
}
