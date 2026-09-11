package net.bestia.zone.chat

import net.bestia.account.Authority
import net.bestia.zone.ai.rumour.RumourKind
import net.bestia.zone.ai.rumour.RumourRegistry
import net.bestia.zone.ai.rumour.RumourService
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

/**
 * Posts news where the caller is standing, and reads back what the towns around them have heard.
 *
 * GM scaffolding, on `SitesChatCommand`'s argument: until something in play posts its own news this is
 * the only way to see whether the reach, the decay and the expiry are right - and all three are
 * decisions a player experiences and nobody can check by reading the table.
 */
@Component
class RumourChatCommand(
  private val rumours: RumourService,
  private val registry: RumourRegistry,
  private val connectionInfoService: ConnectionInfoService,
  private val clock: BestiaClock,
  private val world: WorldView,
  private val out: OutMessageProcessor,
) : ChatCommand() {

  override val requiredAuthority: Authority = Authority.SPAWN

  override fun getHelpText(): String {
    return "/rumour post <STRENGTH> - Posts news of a kill where you stand, strength 0 to 1. " +
      "/rumour here - Lists what the settlements that heard it are still saying. " +
      "/rumour sweep - Forgets everything past its expiry."
  }

  override fun isMatch(cmdText: String): Boolean {
    return cmdText.trim().startsWith(PREFIX)
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val args = cmdText.trim().removePrefix(PREFIX).trim().split(" ").filter { it.isNotBlank() }

    return when (args.firstOrNull()) {
      "post" -> post(playerId, args.getOrNull(1)?.toDoubleOrNull() ?: DEFAULT_STRENGTH)
      "here" -> list(playerId)
      "sweep" -> sweep(playerId)
      else -> false
    }
  }

  private fun post(playerId: Long, strength: Double): Boolean {
    val entityId = connectionInfoService.getActiveEntityId(playerId)
    val position = world.read { get(entityId, Position::class) }?.toVec3L() ?: return false

    val heard = rumours.post(
      kind = RumourKind.BOSS_SLAIN,
      voxelX = position.x,
      voxelY = position.y,
      strength = strength,
    )

    reply(playerId, "Posted a ${RumourKind.BOSS_SLAIN} at strength $strength; ${heard.size} settlement(s) heard it.")

    return true
  }

  private fun list(playerId: Long): Boolean {
    val day = clock.now().absoluteDay
    val towns = registry.settlementsWithNews()

    if (towns.isEmpty()) {
      reply(playerId, "No town has heard anything.")
      return true
    }

    towns.sorted().forEach { settlement ->
      val heard = registry.heardBy(settlement).joinToString(", ") {
        "${it.kind} importance ${it.importanceOn(day)} expires day ${it.expiresOnDay.toInt()}"
      }
      reply(playerId, "Settlement $settlement: $heard")
    }

    return true
  }

  private fun sweep(playerId: Long): Boolean {
    val before = registry.size
    rumours.forgetExpired()

    reply(playerId, "Forgot ${before - registry.size} rumour(s); ${registry.size} still current.")

    return true
  }

  private fun reply(playerId: Long, text: String) {
    out.sendToPlayer(playerId, ChatSMSG(text = text, type = ChatCMSG.Type.COMMAND))
  }

  private companion object {
    const val PREFIX = "/rumour"

    const val DEFAULT_STRENGTH = 0.6
  }
}
