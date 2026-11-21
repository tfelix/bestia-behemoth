package net.bestia.zone.stronghold

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.guild.GuildException
import net.bestia.zone.guild.GuildService
import net.bestia.zone.item.container.HandoverWitness
import net.bestia.zone.item.container.ItemHolder
import org.springframework.stereotype.Component

/** Writes every take from a guild storage into the guild log. */
@Component
class GuildStorageTakeLog(
  private val guildService: GuildService,
) : HandoverWitness {

  override fun handedOver(masterId: Long, from: ItemHolder, to: ItemHolder, itemId: Long, amount: Int) {
    if (from !is ItemHolder.GuildStorage) return

    try {
      guildService.logStorageTake(masterId, itemId, amount)
    } catch (e: GuildException) {
      // Left the guild between the take and this write; the take itself stands.
      LOG.warn { "Master $masterId took $amount of item $itemId from guild ${from.guildId}, but left it: ${e.message}" }
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
