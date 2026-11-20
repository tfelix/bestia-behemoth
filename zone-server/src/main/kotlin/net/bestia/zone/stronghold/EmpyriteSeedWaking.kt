package net.bestia.zone.stronghold

import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.guild.GuildApprovedEvent
import net.bestia.zone.guild.persistence.GuildRepository
import net.bestia.zone.skill.SkillId
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** Keeps as many Empyrite Seeds awake as the number of approved guilds asks for. */
@Component
class EmpyriteSeedWaking(
  private val guilds: GuildRepository,
  private val seeds: EmpyriteSeedRegistry,
  private val sprouts: EmpyriteSeedSprouts,
  private val strongholds: StrongholdRegistry,
  private val world: WorldView,
  private val config: StrongholdConfig,
) {

  /** Boot only, before the tick runs. */
  fun wakeAtBoot() {
    seeds.wakeUpTo(target(approvedGuilds()))
  }

  /** Boot only, once the seeds are awake: their sprouts are not stored. A planted seed shows its site instead. */
  fun raiseSprouts(world: World) {
    seeds.awake().filter { strongholds.on(it.rank) == null }.forEach { sprouts.raise(world, it) }
  }

  /** Counted here, off the tick; woken on it, where the registry lives. */
  @EventListener
  fun onApproved(event: GuildApprovedEvent) {
    val target = target(approvedGuilds())
    world.post { seeds.wakeUpTo(target).forEach { sprouts.raise(this, it) } }
  }

  fun target(approvedGuilds: Long): Int {
    val forGuilds = (approvedGuilds + config.guildsPerSeed - 1) / config.guildsPerSeed

    return maxOf(config.minAwakeSeeds.toLong(), forGuilds).toInt()
  }

  private fun approvedGuilds(): Long {
    return guilds.countWithSkill(SkillId.OFFICIAL_GUILD_APPROVAL.name)
  }
}
