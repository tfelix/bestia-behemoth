package net.bestia.zone.honor

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.honor.persistence.LocalHonorRepository
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.townsfolk.ecs.Townsfolk
import net.bestia.zone.townsfolk.ecs.TownsfolkIdentity
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.persistence.PersistedWorld
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** A world with the real honor rules, a settlement wherever [settlementAt] says, and a clock tests can move. */
class HonorFixture(systems: (HonorService) -> List<System> = { emptyList() }) {

  var now: Instant = Instant.parse("2026-11-03T12:00:00Z")

  /** Which settlement covers a position; out in the country by default. */
  var settlementAt: (Vec3L) -> Int? = { null }

  private val clock = mockk<Clock>().also { every { it.instant() } answers { now } }

  private val worldService = mockk<WorldService>().also {
    every { it.record } returns mockk<PersistedWorld>().also { record ->
      every { record.shapeVersion } returns 1L
      every { record.pipelineVersion } returns 1L
    }
  }

  private val jobs = mockk<AsyncJobExecutor>(relaxed = true)
  private val repository = mockk<LocalHonorRepository>(relaxed = true)

  private val sites = mockk<SettlementSiteIndex>().also {
    every { it.siteCovering(any(), any()) } answers {
      val index = settlementAt(Vec3L(firstArg<Long>(), secondArg<Long>(), 0L))
      index?.let { site -> mockk<SettlementSite> { every { this@mockk.index } returns site } }
    }
  }

  val localHonor = LocalHonorRegistry(repository, jobs, worldService)
  val catalog = HonorCatalog()
  val rules: HonorRules = catalog.rules
  val service = HonorService(catalog, localHonor, sites, clock)
  val world: EcsWorld = testWorld(systems = systems(service))

  private var nextAccount = 1L

  fun master(at: Vec3L, ledger: HonorLedger = HonorLedger()): EntityId {
    val account = nextAccount++
    return world.createEntity { id ->
      add(id, Account(accountId = account))
      add(id, Master(masterId = account * 100))
      add(id, Position.fromVec3(at))
      add(id, StatusEffects())
      add(id, service.componentFor(ledger))
    }
  }

  fun townsperson(at: Vec3L, settlement: Int = VILLAGE, household: Int = 1): Long {
    val identity = TownsfolkIdentity.of(settlement, household, 0)
    world.createEntity { id ->
      add(id, Townsfolk(identity))
      add(id, Position.fromVec3(at))
    }

    return identity
  }

  fun ward(entity: EntityId) {
    world.get(entity, StatusEffects::class)!!.applyEffect(
      definitionId = StatusEffectId.WARDED.id,
      stackBehavior = StackBehavior.REFRESH_DURATION,
      level = 1,
      sourceEntityId = null,
      durationSeconds = 5.0,
      isSyncedToClient = false,
    )
  }

  fun masterIdOf(entity: EntityId): Long {
    return world.get(entity, Master::class)!!.masterId
  }

  fun advance(duration: Duration) {
    now = now.plus(duration)
  }

  companion object {
    const val VILLAGE = 3
  }
}
