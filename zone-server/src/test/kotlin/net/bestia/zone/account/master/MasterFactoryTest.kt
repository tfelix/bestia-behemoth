package net.bestia.zone.account.master

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.account.Account
import net.bestia.zone.account.AccountRepository
import net.bestia.zone.account.master.status.EffortValueCostCalculator
import net.bestia.zone.account.master.status.StatusAttribute
import net.bestia.zone.world.MasterSpawnPointService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Color
import java.util.Optional

class MasterFactoryTest {

  private val accountRepository = mockk<AccountRepository>()
  private val spawnPoints = mockk<MasterSpawnPointService>(relaxed = true)

  private val factory = MasterFactory(
    accountRepository = accountRepository,
    masterRepository = mockk(relaxed = true),
    masterSpawnPointService = spawnPoints,
    entityIdGenerator = mockk(relaxed = true),
    statusEffectPersistenceService = mockk(relaxed = true),
    effortValueCostCalculator = EffortValueCostCalculator(),
    chartService = mockk(relaxed = true),
    cartographyConfig = mockk(relaxed = true),
    worldService = mockk(relaxed = true),
    itemRepository = mockk(relaxed = true),
    inventoryService = mockk(relaxed = true),
  )

  init {
    every { accountRepository.findById(ACCOUNT_ID) } returns Optional.of(Account(ACCOUNT_ID))
  }

  /**
   * Costing each value point by point in `Int` wraps around: this distribution "costs" exactly the creation
   * budget while asking for two billion strength.
   */
  @Test
  fun `an effort value far beyond the budget is refused before it is priced`() {
    val overflowing = StatusAttribute.entries.associateWith { 1 } +
      mapOf(StatusAttribute.STRENGTH to 2_147_472_018, StatusAttribute.AGILITY to 138_536)

    assertThrows<GeneralMasterException> {
      factory.create(ACCOUNT_ID, createData(overflowing))
    }

    verify(exactly = 0) { spawnPoints.ensureComputed() }
  }

  /** Other players read a master's name, so it must not reorder or disguise itself. */
  @Test
  fun `a name that hides a direction override is refused`() {
    assertThrows<InvalidMasterNameException> {
      factory.create(ACCOUNT_ID, createData(StatusAttribute.entries.associateWith { 1 }, name = "Bob\u202Enimda"))
    }
  }

  private fun createData(
    effortValues: Map<StatusAttribute, Int>,
    name: String = "Tester"
  ): MasterFactory.CreateMasterData {
    return MasterFactory.CreateMasterData(
      name = name,
      hairColor = Color.BLACK,
      skinColor = Color.WHITE,
      hair = Hairstyle.HAIR_1,
      face = Face.FACE_1,
      body = BodyType.BODY_M_1,
      spawnPointId = 1,
      effortValues = effortValues
    )
  }

  private companion object {
    const val ACCOUNT_ID = 1L
  }
}
