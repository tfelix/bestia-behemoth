package net.bestia.zone.master.bestia

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.account.Authority
import net.bestia.account.Role
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.transaction.PlatformTransactionManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GmBestiaGrantTest {

  private val connectionInfoService = ConnectionInfoService()
  private val playerBestiaFactory = mockk<PlayerBestiaFactory>()
  private val catalogue = mockk<BestiaCatalogue> {
    every { byIdentifier(GmBestiaGrant.SPECIES) } returns Bestia(
      identifier = GmBestiaGrant.SPECIES,
      level = SPECIES_LEVEL,
      experienceReward = 0,
      health = 1,
      mana = 1
    )
  }

  private val sut = GmBestiaGrant(
    connectionInfoService = connectionInfoService,
    bestiaCatalogue = catalogue,
    playerBestiaFactory = playerBestiaFactory,
    transactionManager = mockk<PlatformTransactionManager>(relaxed = true)
  )

  @Test
  fun `an account without the authority gets nothing`() {
    connectionInfoService.registerAuthenticatedConnection(ACCOUNT, Role.USER.authorities)

    assertFalse(sut.grantIfGm(ACCOUNT, MASTER, AT))

    verify(exactly = 0) { playerBestiaFactory.create(any(), any()) }
  }

  @Test
  fun `an account that never connected gets nothing`() {
    assertFalse(sut.grantIfGm(ACCOUNT, MASTER, AT))

    verify(exactly = 0) { playerBestiaFactory.create(any(), any()) }
  }

  /** No master is selected here, which is exactly the state a master is created in. */
  @Test
  fun `a GM gets the species at its own level, where the master stands`() {
    connectionInfoService.registerAuthenticatedConnection(ACCOUNT, Role.GM.authorities)
    val created = slot<PlayerBestiaFactory.PlayerBestiaCreateData>()
    every { playerBestiaFactory.create(MASTER, capture(created)) } returns mockk<PlayerBestia>()

    assertTrue(sut.grantIfGm(ACCOUNT, MASTER, AT))

    assertEquals(GmBestiaGrant.SPECIES, created.captured.bestiaIdentifier)
    assertEquals(SPECIES_LEVEL, created.captured.level)
    assertEquals(AT, created.captured.spawnPosition)
  }

  @Test
  fun `a failed write is swallowed, so a master that exists is never reported as failed`() {
    connectionInfoService.registerAuthenticatedConnection(ACCOUNT, Role.SUPER_GM.authorities)
    every { playerBestiaFactory.create(any(), any()) } throws IllegalStateException("no slot")

    assertFalse(sut.grantIfGm(ACCOUNT, MASTER, AT))
  }

  @Test
  fun `both GM roles carry the authority and a plain user does not`() {
    assertTrue(Authority.TEST_BESTIA in Role.GM.authorities)
    assertTrue(Authority.TEST_BESTIA in Role.SUPER_GM.authorities)
    assertFalse(Authority.TEST_BESTIA in Role.USER.authorities)
  }

  private companion object {
    const val ACCOUNT = 7L
    const val MASTER = 11L
    const val SPECIES_LEVEL = 80
    val AT = Vec3L(3, 4, 5)
  }
}
