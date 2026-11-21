package net.bestia.zone.stronghold

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.guild.GuildWatchersStrengthenedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** A guild raised Strengthen Watchers: its Strongholds' watchers get the new level at once. */
@Component
class WatcherStrengthening(
  private val strongholds: StrongholdRegistry,
  private val watchers: Watchers,
  private val world: WorldView,
) {

  @EventListener
  fun onStrengthened(event: GuildWatchersStrengthenedEvent) {
    world.post { strongholds.heldBy(event.guildId).forEach { watchers.strengthen(this, it, event.level) } }
  }
}
