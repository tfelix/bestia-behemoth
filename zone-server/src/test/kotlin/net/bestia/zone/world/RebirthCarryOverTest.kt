package net.bestia.zone.world

import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.battle.persistence.PersistedStatusEffect
import net.bestia.zone.battle.persistence.PersistedStatusEffectRepository
import net.bestia.zone.item.persistence.GuildStorage
import net.bestia.zone.item.persistence.GuildStorageRepository
import net.bestia.zone.item.persistence.ItemInstanceRepository
import net.bestia.zone.master.MasterDeletionService
import net.bestia.zone.master.MasterFactory
import net.bestia.zone.postal.persistence.Letter
import net.bestia.zone.postal.persistence.LetterRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.awt.Color
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a master keeps into a new world: themselves and the gear they wear. Their own database, because the
 * carry-over empties every bag and mailbox in it, which would take the fixtures from every other test.
 */
@SpringBootTest(properties = ["spring.datasource.url=jdbc:h2:mem:rebirth-carry-over"])
@ActiveProfiles("no-socket", "test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RebirthCarryOverTest {

  @Autowired
  private lateinit var masterFactory: MasterFactory

  @Autowired
  private lateinit var masterDeletionService: MasterDeletionService

  @Autowired
  private lateinit var masters: MasterRepository

  @Autowired
  private lateinit var instances: ItemInstanceRepository

  @Autowired
  private lateinit var guildStorages: GuildStorageRepository

  @Autowired
  private lateinit var letters: LetterRepository

  @Autowired
  private lateinit var statusEffects: PersistedStatusEffectRepository

  @Autowired
  private lateinit var spawnPoints: MasterSpawnPointService

  @Autowired
  private lateinit var worldService: WorldService

  @Autowired
  private lateinit var events: ApplicationEventPublisher

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val transactions by lazy { TransactionTemplate(transactionManager) }

  @Test
  fun `a master keeps only the gear they wear into a new world`() {
    withFreshMaster { master ->
      val bow = carried(master).getValue("novice_bow").itemInstance!!.id
      val worn = wornInstances(master)
      giveWorldBoundThings(master)

      events.publishEvent(WorldRecreatedEvent(worldService.record, WorldRecreatedEvent.Cause.WORLD_ENDED))

      transactions.execute {
        val kept = masters.findByIdOrThrow(master.id)
        assertTrue(kept.container.slots.all { it.isEquipped }, "only worn gear stays in the bag")
        assertEquals(worn, wornInstances(master))
        assertEquals(emptyList(), kept.container.quiver)
        assertEquals("", kept.warpMarks)
        assertNull(kept.homePostOffice)
      }
      assertFalse(instances.existsById(bow), "a carried item is gone for good")
      assertEquals(0, guildStorages.count())
      assertEquals(0, letters.count())
      assertEquals(0, statusEffects.count())
    }
  }

  @Test
  fun `a world rebuilt for a new build keeps everything`() {
    withFreshMaster { master ->
      val before = carried(master).keys

      events.publishEvent(WorldRecreatedEvent(worldService.record, WorldRecreatedEvent.Cause.MISMATCH))

      assertEquals(before, carried(master).keys)
    }
  }

  private fun giveWorldBoundThings(master: Master) {
    transactions.execute {
      val stored = masters.findByIdOrThrow(master.id)
      stored.warpMarks = "1,2,3"
      stored.homePostOffice = 2
      val potion = stored.container.slots.first { it.template.identifier == "novice_health_potion" }.template
      guildStorages.save(GuildStorage(guildId = GUILD_ID).also { it.container.addStackable(potion, 3) })
    }
    letters.save(Letter(master.id, master.id, "Self", "Note", "Before the end", Instant.now(), Instant.now().plusSeconds(60)))
    statusEffects.save(PersistedStatusEffect(ownerEntityId = 1L, definitionId = 1L))
  }

  private fun carried(master: Master) = transactions.execute {
    masters.findByIdOrThrow(master.id).container.slots
      .filter { !it.isEquipped }
      .associateBy { it.template.identifier }
  }!!

  private fun wornInstances(master: Master): Set<Long> = transactions.execute {
    masters.findByIdOrThrow(master.id).container.slots
      .filter { it.isEquipped }
      .mapNotNull { it.itemInstance?.id }
      .toSet()
  }!!

  private fun withFreshMaster(block: (Master) -> Unit) {
    val master = masterFactory.create(
      ACCOUNT_ID,
      MasterFactory.CreateMasterData(
        name = "reborn${System.nanoTime() % 100_000}",
        hairColor = Color.BLUE,
        skinColor = Color.BLUE,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = spawnPoints.ensureComputed().first().id.toInt(),
        slot = FREE_SLOT,
      )
    )

    try {
      block(master)
    } finally {
      masterDeletionService.delete(ACCOUNT_ID, master.id, master.name)
    }
  }

  private companion object {
    /** Account 3 of the fixture starts with a single master, so slot 1 is free. */
    const val ACCOUNT_ID = 3L
    const val FREE_SLOT = 1
    const val GUILD_ID = 4242L
  }
}
