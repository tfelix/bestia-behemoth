package net.bestia.zone.guild.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.guild.GuildConfig
import net.bestia.zone.guild.GuildNoticeSMSG
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.item.script.ItemScript
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.skill.GuildMates
import net.bestia.zone.skill.SkillId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * The War Banner (`items.yml` id 73): plants the guild's banner on a tile within reach. A guild has one banner;
 * a new one takes the old one down. Every refusal returns false, which keeps the banner in the bag.
 */
@Component
class WarBannerScript(
  private val banners: WarBanners,
  private val config: GuildConfig,
  private val outMessageProcessor: OutMessageProcessor,
) : ItemScript {

  override val itemId = ITEM_ID

  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    val at = args.vec(ScriptArgKeys.POSITION) ?: return false
    val accountId = world.get(userId, Account::class)?.accountId ?: return false
    val membership = world.get(userId, GuildMembership::class)
      ?.takeIf { it.mayUseGuildSkills && it.skillLevel(SkillId.RALLY_TO_THE_BANNER.name) > 0 }
    if (membership == null) {
      outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(OpError.GUILD_BANNER_NOT_ALLOWED))
      return false
    }

    val userAt = world.get(userId, Position::class)?.toVec3L()
    if (userAt == null || userAt.distance(at) > RANGE_TILES) {
      outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(OpError.GUILD_BANNER_OUT_OF_RANGE))
      return false
    }

    val bannerId = world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, EntityVisual(VisualKind.STRUCTURE, StaticEntityKind.WAR_BANNER.ordinal.toLong()))
      add(id, Health(current = config.warBannerHp, max = config.warBannerHp))
      add(id, CombatStats(CombatValues.OBJECT_BASELINE))
      add(id, GuildOwned(membership.guildId))
      add(id, WarBanner(membership.guildId, config.warBannerSeconds.toFloat()))
    }

    banners.plant(membership.guildId, WarBanners.Planted(bannerId, at))
      ?.let { replaced -> world.destroy(replaced.entityId) }

    for (memberAccount in GuildMates.playedBy(world, membership.guildId).keys) {
      outMessageProcessor.sendToPlayer(memberAccount, GuildNoticeSMSG(GuildNoticeSMSG.Notice.BANNER_PLANTED))
    }
    LOG.debug { "Guild ${membership.guildId} planted its war banner ($bannerId) at $at" }

    return true
  }

  companion object {
    const val ITEM_ID = 73L

    /** How far away a banner can be planted, in tiles. The client's aiming cursor uses the same number. */
    const val RANGE_TILES = 5L

    private val LOG = KotlinLogging.logger { }
  }
}
