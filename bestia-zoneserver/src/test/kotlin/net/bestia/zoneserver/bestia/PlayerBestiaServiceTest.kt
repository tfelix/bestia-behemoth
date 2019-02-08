package net.bestia.zoneserver.bestia

import com.nhaarman.mockitokotlin2.whenever
import net.bestia.model.account.Account
import net.bestia.model.account.AccountRepository
import net.bestia.model.battle.BestiaAttackRepository
import net.bestia.model.bestia.PlayerBestia
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.model.findOne
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.entity.PlayerBestiaService
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.util.*

@ExtendWith(MockitoExtension::class)
class PlayerBestiaServiceTest {

  private lateinit var pbService: PlayerBestiaService

  @Mock
  private lateinit var playerBestiaDao: PlayerBestiaRepository

  @Mock
  private lateinit var attackLevelDao: BestiaAttackRepository

  @Mock
  private lateinit var playerBestia: PlayerBestia

  @BeforeEach
  fun setup() {
    ALL_BESTIAS.clear()
    ALL_BESTIAS.add(playerBestia)

    pbService = PlayerBestiaService(playerBestiaDao, attackLevelDao)
  }

  @Test
  fun getAllBestias_wrongAccId_empty() {
    whenever(playerBestiaDao.findMasterBestiaForAccount(WRONG_ACC_ID)).thenReturn(null)

    val bestias = pbService.getAllBestias(WRONG_ACC_ID)
    assertThat(bestias, hasSize(0))
  }

  @Test
  fun getAllBestias_okAccId_allBestias() {
    whenever(playerBestiaDao.findPlayerBestiasForAccount(OK_ACC_ID)).thenReturn(setOf(playerBestia))
    val bestias = pbService.getAllBestias(OK_ACC_ID)

    assertThat(bestias, hasSize(ALL_BESTIAS.size))
  }

  companion object {
    private const val WRONG_ACC_ID: Long = 2
    private const val OK_ACC_ID: Long = 1
    private val ALL_BESTIAS = ArrayList<PlayerBestia>()
    private const val OK_PLAYERBESTIA_ID: Long = 10
    private const val WRONG_PLAYERBESTIA_ID: Long = 11
  }
}
