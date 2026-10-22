package net.bestia.zone.guild.skill

import net.bestia.zone.battle.ecs.damage.PlayerDeathSystem
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.guild.GuildNoticeSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.skill.GuildMates
import org.springframework.stereotype.Component as SpringComponent

/**
 * Takes a War Banner down when enemies cut it down or its time runs out, and tells the guild.
 *
 * In the death phase before `DeathSystem` removes the dead, so a fallen banner is seen before it is gone.
 */
@SpringComponent
class WarBannerSystem(
  private val banners: WarBanners,
  private val outMessageProcessor: OutMessageProcessor,
) : System {

  override val phase = Phase.DEATH
  override val before = setOf(PlayerDeathSystem::class)

  override val reads: ComponentClassSet = setOf(Dead::class) + GuildMates.reads
  override val writes: ComponentClassSet = setOf(WarBanner::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(WarBanner::class).each { id ->
      val banner = get<WarBanner>()
      banner.remainingSeconds -= deltaTime

      val notice = when {
        world.has(id, Dead::class) -> GuildNoticeSMSG.Notice.BANNER_FALLEN
        banner.remainingSeconds <= 0f -> GuildNoticeSMSG.Notice.BANNER_FADED
        else -> return@each
      }

      banners.remove(banner.guildId, id)
      if (notice == GuildNoticeSMSG.Notice.BANNER_FADED) {
        world.destroy(id)
      }
      for (accountId in GuildMates.playedBy(world, banner.guildId).keys) {
        outMessageProcessor.sendToPlayer(accountId, GuildNoticeSMSG(notice))
      }
    }
  }
}
