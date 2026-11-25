package net.bestia.zone.master.bestia

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.master.bestia.ecs.OfferedForEtching
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.essence.EssenceDefinition
import net.bestia.zone.item.essence.EssenceEffect
import net.bestia.zone.item.essence.Essence
import net.bestia.zone.item.essence.GemTier
import net.bestia.zone.item.essence.SocketGroup
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BestiaEtchingTest {

  private val world = testWorld()

  private var tableInReach = true

  private val structures = mockk<PlayerStructureService> {
    every { stationNear(any(), any(), StaticEntityKind.CARVING_TABLE) } answers { if (tableInReach) 1L else null }
  }

  private val catalogue = mockk<BestiaCatalogue> {
    every { find(BOAR) } returns species(BOAR, "boar", EssenceDefinition(SocketGroup.WEAPON, EssenceEffect.BANE, "BEAST"))
    every { find(CRITTER) } returns species(CRITTER, "critter", essence = null)
    every { find(BLOB_QUEEN) } returns species(
      BLOB_QUEEN, "blob_queen", EssenceDefinition(SocketGroup.ARMOR, EssenceEffect.BOSS, "ROYAL_JELLY"), bossLevel = 42
    )
  }

  private val itemTemplates = mockk<ItemTemplateRegistry> {
    every { identifierOf(CUT_AMETHYST) } returns "cut_amethyst"
    every { identifierOf(CUT_RUBY) } returns "cut_ruby"
    every { identifierOf(CUT_PYRELITH) } returns "cut_pyrelith"
  }

  private val loss = mockk<PlayerBestiaLoss>(relaxed = true)

  private val connections = mockk<ConnectionInfoService> {
    every { getSelectedMasterEntityId(OWNER_ACCOUNT) } returns OWNER_ENTITY
  }

  private val offers = EtchingOffers(structures, connections, mockk(relaxed = true))

  private val etching = BestiaEtching(catalogue, itemTemplates, structures, loss, offers)

  @Test
  fun `an own Bestia at the table may be etched`() {
    val boar = givenBestia(level = 6)

    assertNull(etching.refusal(world, MASTER_ID, boar, CUT_AMETHYST))
  }

  @Test
  fun `a Bestia below the gem's level is refused`() {
    val boar = givenBestia(level = 40)

    assertEquals(OpError.CRAFT_NOT_POSSIBLE, etching.refusal(world, MASTER_ID, boar, CUT_RUBY))
  }

  @Test
  fun `another master's Bestia may be etched only once its owner offers it`() {
    val boar = givenBestia(level = 6, masterId = MASTER_ID + 1)
    world.add(boar, Account(OWNER_ACCOUNT))

    assertEquals(OpError.CRAFT_NOT_POSSIBLE, etching.refusal(world, MASTER_ID, boar, CUT_AMETHYST))

    world.add(boar, OfferedForEtching)
    assertNull(etching.refusal(world, MASTER_ID, boar, CUT_AMETHYST))
    assertEquals(OWNER_ENTITY, etching.ownerOf(world, boar))
  }

  @Test
  fun `a dead Bestia and a species without an essence are refused`() {
    val dead = givenBestia(level = 6).also { world.add(it, Dead()) }
    val critter = givenBestia(level = 6, speciesId = CRITTER)

    assertEquals(OpError.CRAFT_NOT_POSSIBLE, etching.refusal(world, MASTER_ID, dead, CUT_AMETHYST))
    assertEquals(OpError.CRAFT_NOT_POSSIBLE, etching.refusal(world, MASTER_ID, critter, CUT_AMETHYST))
  }

  @Test
  fun `a boss fills only the gem of its level`() {
    val queen = givenBestia(level = 42, speciesId = BLOB_QUEEN)

    assertNull(etching.refusal(world, MASTER_ID, queen, CUT_RUBY))
    assertEquals(OpError.CRAFT_NOT_POSSIBLE, etching.refusal(world, MASTER_ID, queen, CUT_AMETHYST))
    assertEquals(OpError.CRAFT_NOT_POSSIBLE, etching.refusal(world, MASTER_ID, queen, CUT_PYRELITH))
  }

  @Test
  fun `a Bestia away from the table is told to come closer`() {
    tableInReach = false
    val boar = givenBestia(level = 6)

    assertEquals(OpError.CRAFT_BESTIA_NOT_AT_TABLE, etching.refusal(world, MASTER_ID, boar, CUT_AMETHYST))
  }

  @Test
  fun `binding takes the Bestia and answers its species in the gem's tier`() {
    val boar = givenBestia(level = 45)

    assertEquals(Essence(BOAR, GemTier.RUBY), etching.bind(world, boar, CUT_RUBY))
    verify { loss.lose(world, boar) }
  }

  private fun givenBestia(level: Int, masterId: Long = MASTER_ID, speciesId: Long = BOAR): EntityId =
    world.createEntity { id ->
      add(id, OwnedBestia(masterId = masterId, playerBestiaId = 1L))
      add(id, EntityVisual(VisualKind.BESTIA, speciesId))
      add(id, Level(level))
      add(id, Position(0, 0, 0))
    }

  private fun species(id: Long, identifier: String, essence: EssenceDefinition?, bossLevel: Int? = null): Bestia {
    val level = bossLevel ?: 1
    return Bestia(
      id = id, identifier = identifier, levelMin = level, levelMax = level, experienceReward = 0, health = 1, mana = 1,
      essence = essence, boss = bossLevel != null
    )
  }

  private companion object {
    const val MASTER_ID = 42L
    const val BOAR = 6L
    const val CRITTER = 98L
    const val BLOB_QUEEN = 79L
    const val CUT_AMETHYST = 136L
    const val CUT_RUBY = 138L
    const val CUT_PYRELITH = 139L
    const val OWNER_ACCOUNT = 7L
    const val OWNER_ENTITY = 500L
  }
}
