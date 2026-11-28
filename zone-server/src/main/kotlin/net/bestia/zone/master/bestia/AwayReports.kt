package net.bestia.zone.master.bestia

import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.ai.ecs.OwnedBestiaStates
import net.bestia.zone.bestia.diary.AwayReport
import net.bestia.zone.bestia.diary.BestiaDiary
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.Dead
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** What each of a master's bestias did since the master last left the world. Reads the database. */
@Service
class AwayReports(
  private val masters: MasterRepository,
  private val playerBestias: PlayerBestiaRepository,
  private val diary: BestiaDiary,
  private val world: WorldView,
  private val activities: BestiaActivities,
  private val states: OwnedBestiaStates,
  private val clock: Clock = Clock.systemUTC(),
) {

  data class Report(val since: Instant, val bestias: List<AwayReport>)

  @Transactional(readOnly = true)
  fun of(masterId: Long): Report? {
    val master = masters.findByIdOrNull(masterId) ?: return null
    val until = clock.instant()
    val since = master.loggedOutAtEpochMs?.let(Instant::ofEpochMilli) ?: until.minus(WITHOUT_LOGOUT)
    val rows = playerBestias.findAllByMasterIdAndStoredAtIsNull(masterId).sortedBy { it.id }
    val subjects = world.read { rows.map { subjectOf(this, it) } }
    val lines = diary.of(masterId)

    return Report(since, subjects.map { AwayReport.of(it, lines, since, until) })
  }

  private fun subjectOf(world: World, row: PlayerBestia): AwayReport.Subject {
    val subject = AwayReport.Subject(row.id, row.name, row.bestia.identifier, now = AwayReport.NOT_IN_WORLD)
    val entityId = row.entityId?.takeIf(world::isAlive) ?: return subject
    if (world.has(entityId, Dead::class)) return subject.copy(now = AwayReport.DEAD)
    val activity = activities.of(world, entityId, row.aiConfig.job) ?: return subject

    return subject.copy(now = activity.kind, nowAt = activity.station, state = states.of(world, entityId))
  }

  private companion object {
    /** A master who never left since logout times were kept reads about the last day. */
    val WITHOUT_LOGOUT: Duration = Duration.ofHours(24)
  }
}
