package net.bestia.zone.guild.skill

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.guild.GuildConfig
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.NoActiveSessionException
import net.bestia.zone.skill.SkillId
import net.bestia.zone.spawn.ecs.LootChanceModifier
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Component

/** The guild skill Fortune's Favor: what a member kills drops its items more often, wherever the member is. */
@Component
class FortunesFavor(
  private val sessions: ConnectionInfoService,
  private val config: GuildConfig,
) : LootChanceModifier {

  override val reads: ComponentClassSet = setOf(GuildMembership::class)

  override fun factor(world: World, killerAccountId: AccountId): Double {
    val master = try {
      sessions.getSelectedMasterEntityId(killerAccountId)
    } catch (_: NoActiveSessionException) {
      return 1.0
    }
    val level = world.get(master, GuildMembership::class)?.skillLevel(SkillId.FORTUNES_FAVOR.name) ?: 0

    return 1.0 + level * config.fortunePercentPerLevel / 100.0
  }
}
