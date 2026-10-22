package net.bestia.zone.guild.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/**
 * Gives every master in a guild, and each of its bestias, the [GuildTag] its [GuildMembership] describes, and
 * takes it away once the master is in no guild. A bestia whose master is not in the world carries none.
 */
@SpringComponent
class GuildTagSystem : System {
  override val phase = Phase.UPKEEP

  override val schedule: Schedule = Schedule.EverySeconds(1f)

  override val reads: ComponentClassSet = setOf(GuildMembership::class, Master::class, OwnedBestia::class)

  override val writes: ComponentClassSet = setOf(GuildTag::class)

  override fun update(world: World, deltaTime: Float) {
    val tagsByMaster = HashMap<Long, GuildTag>()
    world.query(GuildMembership::class, Master::class).each {
      tagsByMaster[get<Master>().masterId] = GuildTag.of(get<GuildMembership>())
    }

    val wanted = HashMap<EntityId, GuildTag?>()
    world.query(Master::class).each { id -> wanted[id] = tagsByMaster[get<Master>().masterId] }
    world.query(OwnedBestia::class).each { id -> wanted[id] = tagsByMaster[get<OwnedBestia>().masterId] }

    wanted.forEach { (id, tag) -> apply(world, id, tag) }
  }

  private fun apply(world: World, id: EntityId, wanted: GuildTag?) {
    val current = world.get(id, GuildTag::class)
    if (current == wanted) {
      return
    }

    if (wanted == null) {
      world.remove(id, GuildTag::class)
    } else {
      world.add(id, wanted)
    }
  }
}
