package net.bestia.zone.guild.skill

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * The banner each guild has standing, so Rally to the Banner finds it without a cast walking the world. Kept by
 * [WarBannerScript], which plants one, and [WarBannerSystem], which takes it down.
 */
@Component
class WarBanners {

  data class Planted(val entityId: EntityId, val position: Vec3L)

  private val byGuild = ConcurrentHashMap<Long, Planted>()

  fun of(guildId: Long): Planted? {
    return byGuild[guildId]
  }

  /** @return the banner it replaces, which the caller takes down */
  fun plant(guildId: Long, banner: Planted): Planted? {
    return byGuild.put(guildId, banner)
  }

  /** Only if [entityId] is still the guild's banner: a replaced one must not take its successor with it. */
  fun remove(guildId: Long, entityId: EntityId) {
    byGuild.computeIfPresent(guildId) { _, planted -> planted.takeUnless { it.entityId == entityId } }
  }
}
