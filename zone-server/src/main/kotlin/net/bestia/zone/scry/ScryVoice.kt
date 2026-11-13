package net.bestia.zone.scry

import net.bestia.zone.casting.ScrySight
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkSubscriptionService
import org.springframework.stereotype.Component

/** While its sight is away, a Sage speaks where it looks: whoever holds that ground hears it, and not the body's neighbours. */
@Component
class ScryVoice(
  private val subscriptions: ChunkSubscriptionService,
  private val chunkService: ChunkService,
) {

  /** Who hears [speakerId] in public chat, or null when it is not scrying and speaks as usual. Tick thread. */
  fun listenersOf(world: World, speakerId: EntityId, speakerAccount: AccountId): Set<AccountId>? {
    val sight = world.get(speakerId, ScrySight::class) ?: return null
    val ground = chunkService.normalise(ChunkCoords.chunkOf(chunkService.config, sight.centre))

    return subscriptions.subscribersOf(ground) + speakerAccount
  }
}
