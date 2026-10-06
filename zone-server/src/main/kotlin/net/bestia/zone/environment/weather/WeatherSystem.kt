package net.bestia.zone.environment.weather

import net.bestia.zone.identity.ecs.ActivePlayer
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.world.stream.ChunkStreamSystem
import org.springframework.stereotype.Component as SpringComponent

/**
 * Keeps every online player's sky up to date.
 *
 * ### Cheap by construction
 *
 * `writes` is empty, so `SystemScheduler` may run this beside any system that does not move entities. The work is one
 * O(1) field evaluation per online player per interval, so a thousand players at ten seconds is a hundred
 * evaluations a second - which is why the interval is a real setting rather than a tick budget.
 *
 * ### This is the steady state, not the first message
 *
 * A player's *first* weather comes from `SelectMasterHandler` the moment their master is spawned, because
 * waiting for this sweep meant up to `weather.evaluation-seconds`.
 * Both go through [WeatherPublisher], which owns the dedup state, so the handover costs no duplicate message.
 */
@SpringComponent
class WeatherSystem(
  private val publisher: WeatherPublisher,
  private val config: WeatherConfig,
) : System {
  override val phase = Phase.WORLD
  override val after = setOf(ChunkStreamSystem::class)

  override val schedule: Schedule get() = Schedule.EverySeconds(config.evaluationSeconds)

  override val reads: ComponentClassSet = setOf(Account::class, ActivePlayer::class, KnownSkills::class, Position::class)

  /** Deliberately empty: this changes no component. */
  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    if (!config.enabled) return

    val seen = HashSet<Long>()

    world.query(Position::class, Account::class, ActivePlayer::class).each { entityId ->
      val accountId = get<Account>().accountId
      val position = get<Position>()
      seen.add(accountId)

      val level = publisher.weatherSenseSkillId?.let { skillId ->
        world.get(entityId, KnownSkills::class)?.levelOf(skillId)
      } ?: 0

      publisher.publish(accountId, position.x, position.y, level)
    }

    publisher.retainOnly(seen)
  }
}
