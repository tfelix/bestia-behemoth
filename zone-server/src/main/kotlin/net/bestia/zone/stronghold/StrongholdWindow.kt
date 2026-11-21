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
import java.time.Clock
import java.time.Duration
import kotlin.random.Random

/**
 * The Stronghold window at a guild's Empyrite: members open it, invest coins one step of one track per click, hire
 * its steward, and call its watchers. Tick thread only.
 */
@Service
class StrongholdWindow(
  private val strongholds: StrongholdRegistry,
  private val seeds: EmpyriteSeedRegistry,
  private val empyrites: Empyrites,
  private val stewards: Stewards,
  private val watchers: Watchers,
  private val coins: StrongholdCoins,
  private val messages: OutMessageProcessor,
  private val config: StrongholdConfig,
  private val random: Random = Random.Default,
  private val clock: Clock = Clock.systemUTC(),
) {

  /** A member clicked their guild's Empyrite: the window opens. */
  fun open(world: World, actorId: EntityId, empyriteId: EntityId): OpError? {
    refusal(world, actorId, empyriteId)?.let { return it }
    // The member carries the guild's skill levels; the Stronghold keeps Strengthen Watchers for when nobody is near.
    val stronghold = strongholdOf(world, empyriteId) ?: return OpError.REQUEST_REFUSED
    watchers.strengthen(world, stronghold, guildSkillLevel(world, actorId, SkillId.STRENGTHEN_WATCHERS))
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
    if (track == StrongholdTrack.DEFENSE && advanced.level != progress.level) {
      empyrites.harden(world, stronghold.rank)
      strongholds.on(stronghold.rank)?.let { watchers.levelUp(world, it) }
    }

    sendWindow(world, actorId, empyriteId)

    return null
  }

  /** The member pays for a steward, which stands beside the Empyrite at once. Needs Steward Contract. */
  fun hireSteward(world: World, actorId: EntityId, empyriteId: EntityId): OpError? {
    refusal(world, actorId, empyriteId)?.let { return it }
    val stronghold = strongholdOf(world, empyriteId) ?: return OpError.REQUEST_REFUSED
    val masterId = world.get(actorId, Master::class)?.masterId ?: return OpError.REQUEST_REFUSED

    // The window offers no button then, so only a stale window or a hand-made request asks.
    if (!hasStewardContract(world, actorId) || stronghold.hasSteward) return OpError.REQUEST_REFUSED

    if (!coins.pay(world, actorId, masterId, config.stewardCoins)) return OpError.STRONGHOLD_CANNOT_AFFORD_STEWARD

    stewards.hire(world, stronghold)
    sendWindow(world, actorId, empyriteId)

    return null
  }

  /**
   * The leader adds or removes one watcher of [kind]. Calling one is free; the posts cap the mix. Needs Watcher
   * Research.
   */
  fun changeWatchers(world: World, actorId: EntityId, empyriteId: EntityId, kind: WatcherKind, delta: Int): OpError? {
    refusal(world, actorId, empyriteId)?.let { return it }
    val stronghold = strongholdOf(world, empyriteId) ?: return OpError.REQUEST_REFUSED
    val count = stronghold.watchersOf(kind) + delta
    val total = stronghold.watchers.values.sum() + delta

    // The window offers no such button, so only a stale window or a hand-made request asks.
    if (!maySetWatchers(world, actorId) || count < 0 || total > watchers.postsOf(stronghold)) {
      return OpError.REQUEST_REFUSED
    }

    watchers.setMix(world, stronghold, kind, count)
    sendWindow(world, actorId, empyriteId)

    return null
  }

  /** Any member pays for the fallen watcher that would wait longest to come back at once. */
  fun bringBackWatcher(world: World, actorId: EntityId, empyriteId: EntityId): OpError? {
    refusal(world, actorId, empyriteId)?.let { return it }
    val stronghold = strongholdOf(world, empyriteId) ?: return OpError.REQUEST_REFUSED
    val masterId = world.get(actorId, Master::class)?.masterId ?: return OpError.REQUEST_REFUSED

    if (watchers.fallenOf(stronghold.rank).isEmpty()) return OpError.REQUEST_REFUSED
    if (!coins.pay(world, actorId, masterId, config.watcherBringBackCoins)) {
      return OpError.STRONGHOLD_CANNOT_AFFORD_BRING_BACK
    }

    watchers.bringBack(world, stronghold)
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

    val steward = StrongholdSMSG.Steward(hasStewardContract(world, actorId), stronghold.hasSteward, config.stewardCoins)

    val window = StrongholdSMSG(
      empyriteId, seedName, guildName, health.current, health.max, tracks, hasPermanentDevelopment(world, actorId),
      config.empyriteHpPerDefense, config.empyriteDefensePerLevel, steward, watcherInfo(world, actorId, stronghold)
    )
    messages.sendToPlayer(accountId, window)
  }

  private fun watcherInfo(world: World, actorId: EntityId, stronghold: Stronghold): StrongholdSMSG.Watchers {
    val now = clock.instant()

    return StrongholdSMSG.Watchers(
      hasResearch = hasGuildSkill(world, actorId, SkillId.WATCHER_RESEARCH),
      maySet = maySetWatchers(world, actorId),
      posts = watchers.postsOf(stronghold),
      level = watchers.levelOf(stronghold),
      mix = WatcherKind.entries.associateWith { stronghold.watchersOf(it) },
      fallen = watchers.fallenOf(stronghold.rank).map { it.kind to Duration.between(now, it.backAt).seconds.coerceAtLeast(0) },
      bringBackCoins = config.watcherBringBackCoins,
    )
  }

  /** The docs leave the mix to the leader, with Watcher Research. */
  private fun maySetWatchers(world: World, actorId: EntityId): Boolean {
    val membership = world.get(actorId, GuildMembership::class) ?: return false

    return membership.isLeader && hasGuildSkill(world, actorId, SkillId.WATCHER_RESEARCH)
  }

  /** A step towards level N costs N times the base. */
  private fun stepCoins(progress: TrackProgress): Long {
    return config.stepCoinsPerLevel * (progress.level + 1)
  }

  private fun hasPermanentDevelopment(world: World, actorId: EntityId): Boolean {
    return hasGuildSkill(world, actorId, SkillId.PERMANENT_DEVELOPMENT)
  }

  private fun hasStewardContract(world: World, actorId: EntityId): Boolean {
    return hasGuildSkill(world, actorId, SkillId.STEWARD_CONTRACT)
  }

  private fun hasGuildSkill(world: World, actorId: EntityId, skill: SkillId): Boolean {
    return guildSkillLevel(world, actorId, skill) > 0
  }

  private fun guildSkillLevel(world: World, actorId: EntityId, skill: SkillId): Int {
    return world.get(actorId, GuildMembership::class)?.skillLevel(skill.name) ?: 0
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
