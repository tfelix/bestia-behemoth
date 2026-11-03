package net.bestia.zone.casting.ecs

import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.battle.ecs.skill.Channel
import net.bestia.zone.casting.SkillExecutionService
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/** Runs every [Channel] down, letting its skill act at each interval, and ends it when it runs out. */
@SpringComponent
class ChannelSystem(
  private val skillExecutionService: SkillExecutionService,
  private val castCancelService: CastCancelService,
) : System {
  override val phase = Phase.ACTIONS
  override val after = setOf(CastingSystem::class)

  override val writes: ComponentClassSet = setOf(Channel::class)

  override fun update(world: World, deltaTime: Float) {
    val done = mutableListOf<EntityId>()

    world.query(Channel::class).each { id ->
      val channel = get<Channel>()
      channel.countdown(deltaTime)
      channel.sinceLastTick += deltaTime

      while (channel.sinceLastTick >= channel.intervalSeconds) {
        channel.sinceLastTick -= channel.intervalSeconds
        skillExecutionService.channelTick(world, id, channel)
        channel.ticks++
      }

      if (channel.hasElapsed()) done.add(id)
    }

    // Deferred, because ending a channel also drops the status that holds the caster still.
    if (done.isNotEmpty()) {
      world.defer { done.forEach { castCancelService.cancelCast(world, it) } }
    }
  }
}
