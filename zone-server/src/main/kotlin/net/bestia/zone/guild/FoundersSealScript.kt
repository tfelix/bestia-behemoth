package net.bestia.zone.guild

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.script.ItemScript
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * The Founder's Seal is spent by founding a guild, never by using it: founding needs a name the database has to
 * accept first, which an item script - run inside the world, where no SQL may happen - cannot wait for. The
 * client therefore sends `CreateGuildCMSG` instead, and [GuildService.found] takes the seal.
 *
 * This only exists because every usable item needs a script, and it always refuses, which keeps the seal.
 */
@Component
class FoundersSealScript : ItemScript {

  override val itemId: Long = ITEM_ID

  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    LOG.debug { "Entity $userId used a Founder's Seal directly; founding goes through CreateGuildCMSG" }
    return false
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** `founders_seal` in items.yml. */
    const val ITEM_ID = 72L
  }
}
