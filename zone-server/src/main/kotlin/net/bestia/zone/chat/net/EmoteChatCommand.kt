package net.bestia.zone.chat.net

import net.bestia.zone.chat.ChatCommand
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.emote.EmoteService
import net.bestia.zone.entity.ecs.Animation.AnimationKind
import org.springframework.stereotype.Component

/**
 * `/cheer`, `/sit` and every other emote - one command per emote kind of [AnimationKind], named after it, so a new
 * emote is typed and listed in `/help` the moment it exists and there is no list here to keep in step.
 *
 * Chat is dispatched on an IO thread, so the emote is posted to the tick, where [EmoteService] answers any refusal
 * itself.
 */
@Component
class EmoteChatCommand(
  private val world: WorldView,
  private val emoteService: EmoteService,
) : ChatCommand() {

  override fun getHelpText(): String {
    val commands = AnimationKind.EMOTES.joinToString(", ") { "/${it.command}" }
    return "$commands - Plays an emote. Bind them to Alt+1 to Alt+0 in the emote window (Alt+E)."
  }

  override fun isMatch(cmdText: String) = kindOf(cmdText) != null

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val kind = kindOf(cmdText) ?: return false
    world.post { emoteService.perform(this, playerId, kind) }

    return true
  }

  private fun kindOf(cmdText: String): AnimationKind? {
    val word = cmdText.trim().takeIf { it.startsWith("/") }?.drop(1) ?: return null
    return AnimationKind.EMOTES.firstOrNull { it.command == word }
  }
}
