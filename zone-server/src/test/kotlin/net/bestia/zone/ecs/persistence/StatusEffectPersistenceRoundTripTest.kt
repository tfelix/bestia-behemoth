package net.bestia.zone.ecs.persistence

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.battle.effects.StatusEffects
import net.bestia.zone.ecs.battle.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.SnowflakeEntityIdGenerator
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.PersistedStatusEffectRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the status effect store against the real repository and (in-memory) DB. Uses isolated,
 * non-ticking [World]s rather than the Spring-managed one so `StatusEffectDurationSystem` cannot eat
 * the remaining seconds the assertions are about.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class StatusEffectPersistenceRoundTripTest {

  @Autowired
  private lateinit var statusEffectPersistenceService: StatusEffectPersistenceService

  @Autowired
  private lateinit var statusEffectService: StatusEffectService

  @Autowired
  private lateinit var persistedStatusEffectRepository: PersistedStatusEffectRepository

  @BeforeEach
  fun clean() {
    persistedStatusEffectRepository.deleteAll()
  }

  @Test
  fun `an effect seeded before the entity exists is attached when it is finally spawned`() {
    val entityId = 4711L

    // The MasterFactory case: nothing is alive yet, the id is simply reserved.
    statusEffectPersistenceService.seed(entityId, StatusEffectId.MASTER_INTRO_MARKER)

    val world = newWorld()
    val loaded = statusEffectPersistenceService.load(entityId)
    world.createEntity(entityId) { id -> statusEffectPersistenceService.attach(this, id, loaded) }

    world.read {
      val effects = get(entityId, StatusEffects::class)
      assertNotNull(effects, "the seeded effect must produce a StatusEffects component")

      val marker = effects.activeEffects.single()
      assertEquals(StatusEffectId.MASTER_INTRO_MARKER.id, marker.definitionId)
      assertEquals(
        Float.POSITIVE_INFINITY,
        marker.remainingSeconds,
        "a null duration column means the effect never expires"
      )
      assertTrue(
        has(entityId, IsStatusValueDirty::class),
        "restoring effects must schedule a recalc, otherwise they change nothing"
      )
    }
  }

  @Test
  fun `seeding the same effect twice stores it once`() {
    val entityId = 4712L

    statusEffectPersistenceService.seed(entityId, StatusEffectId.MASTER_INTRO_MARKER)
    statusEffectPersistenceService.seed(entityId, StatusEffectId.MASTER_INTRO_MARKER)

    assertEquals(1, persistedStatusEffectRepository.findAllByOwnerEntityId(entityId).size)
  }

  @Test
  fun `a finite effect round-trips with its remaining seconds`() {
    val spawnWorld = newWorld()
    val entityId = spawnWorld.createEntity { }

    spawnWorld.modify(entityId) { id ->
      statusEffectService.applyEffect(this, id, StatusEffectId.SWIFTNESS, level = 1)
    }

    val applied = spawnWorld.read {
      get(entityId, StatusEffects::class)!!.activeEffects.single().remainingSeconds
    }

    val snapshot = spawnWorld.read { statusEffectPersistenceService.snapshot(this, entityId) }
    assertNotNull(snapshot)
    statusEffectPersistenceService.persist(listOf(snapshot))

    // Fresh world standing in for a relog: offline time deliberately does not tick the buff down.
    val reloadWorld = newWorld()
    val loaded = statusEffectPersistenceService.load(entityId)
    reloadWorld.createEntity(entityId) { id -> statusEffectPersistenceService.attach(this, id, loaded) }

    reloadWorld.read {
      val restored = get(entityId, StatusEffects::class)!!.activeEffects.single()
      assertEquals(StatusEffectId.SWIFTNESS.id, restored.definitionId)
      assertEquals(applied, restored.remainingSeconds)
    }
  }

  @Test
  fun `persisting an entity whose effects are gone clears its stored rows`() {
    val world = newWorld()
    val entityId = world.createEntity { }

    world.modify(entityId) { id ->
      statusEffectService.applyEffect(this, id, StatusEffectId.SWIFTNESS, level = 1)
    }
    statusEffectPersistenceService.persist(
      listOfNotNull(world.read { statusEffectPersistenceService.snapshot(this, entityId) })
    )
    assertEquals(1, persistedStatusEffectRepository.findAllByOwnerEntityId(entityId).size)

    world.modify(entityId) { id ->
      get(id, StatusEffects::class)!!.removeEffect(StatusEffectId.SWIFTNESS.id)
    }
    statusEffectPersistenceService.persist(
      listOfNotNull(world.read { statusEffectPersistenceService.snapshot(this, entityId) })
    )

    assertTrue(
      persistedStatusEffectRepository.findAllByOwnerEntityId(entityId).isEmpty(),
      "an emptied effect list must delete the rows, not leave the old ones behind"
    )
  }

  @Test
  fun `an entity without the component is left alone rather than having its rows wiped`() {
    val entityId = 4713L
    statusEffectPersistenceService.seed(entityId, StatusEffectId.MASTER_INTRO_MARKER)

    val world = newWorld()
    world.createEntity(entityId) { }

    // No StatusEffects component means "not participating", which must not be confused with
    // "has no effects" - otherwise a kind that never restores would erase its own stored state.
    assertNull(world.read { statusEffectPersistenceService.snapshot(this, entityId) })
    assertEquals(1, persistedStatusEffectRepository.findAllByOwnerEntityId(entityId).size)
  }

  /** An isolated, non-ticking world so the duration system can't perturb the assertions. */
  private fun newWorld() = World(idGenerator = SnowflakeEntityIdGenerator(), systems = emptyList())
}
