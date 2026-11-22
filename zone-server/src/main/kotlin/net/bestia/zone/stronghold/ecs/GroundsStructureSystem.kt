package net.bestia.zone.stronghold.ecs

import net.bestia.zone.compendium.ecs.CompendiumSightingSystem
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.TreeRegrowthSystem
import net.bestia.zone.stronghold.StrongholdGrounds
import net.bestia.zone.util.EntityId
import net.bestia.zone.weather.EnvironmentalExposureSystem
import org.springframework.stereotype.Component as SpringComponent

/**
 * Every building on a Stronghold's grounds is a structure of the guild that holds it: only rival guilds with Approval
 * can damage it, and only during an attack window. A building off all grounds belongs to nobody's guild.
 *
 * Read off where it stands rather than stored, so a capture hands the buildings over with the grounds. A building's
 * entity comes and goes with its chunk; a sweep every two seconds catches a new one.
 */
@SpringComponent
class GroundsStructureSystem(
  private val grounds: StrongholdGrounds,
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(2f)
  override val phase = Phase.UPKEEP
  override val before = setOf(AttackWindowSystem::class)
  override val after = setOf(EnvironmentalExposureSystem::class, CompendiumSightingSystem::class, TreeRegrowthSystem::class)

  override val reads: ComponentClassSet = setOf(PlayerStructureIdentity::class, Position::class, PropPose::class)
  override val writes: ComponentClassSet = setOf(GuildOwned::class)

  override fun update(world: World, deltaTime: Float) {
    val changes = ArrayList<Pair<EntityId, Long?>>()
    world.query(PlayerStructureIdentity::class).each { id ->
      val at = world.get(id, Position::class)?.toVec3L() ?: world.get(id, PropPose::class)?.position ?: return@each
      val guildId = grounds.strongholdAt(at)?.guildId
      if (world.get(id, GuildOwned::class)?.guildId != guildId) changes.add(id to guildId)
    }

    for ((id, guildId) in changes) {
      if (guildId == null) world.remove(id, GuildOwned::class) else world.add(id, GuildOwned(guildId))
    }
  }
}
