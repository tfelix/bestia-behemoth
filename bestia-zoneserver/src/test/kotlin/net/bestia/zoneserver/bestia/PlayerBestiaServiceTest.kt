package net.bestia.zoneserver.bestia

import com.nhaarman.mockitokotlin2.whenever
import net.bestia.model.account.Account
import net.bestia.model.account.AccountRepository
import net.bestia.model.battle.BestiaAttackRepository
import net.bestia.model.bestia.PlayerBestia
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.entity.PlayerBestiaService
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.util.*

@ExtendWith(MockitoExtension::class)
class PlayerBestiaServiceTest {

  private lateinit var pbService: PlayerBestiaService

  @Mock
  private lateinit var accountDao: AccountRepository

  @Mock
  private lateinit var playerBestiaDao: PlayerBestiaRepository

  @Mock
  private lateinit var attackLevelDao: BestiaAttackRepository

  @Mock
  private lateinit var playerBestia: PlayerBestia

  @Mock
  private lateinit var account: Account

  @BeforeEach
  fun setup() {
    ALL_BESTIAS.clear()
    ALL_BESTIAS.add(playerBestia)

    // whenever(accountDao.findById(OK_ACC_ID)).thenReturn(Optional.of(account))
    whenever(playerBestiaDao.findOneOrThrow(OK_PLAYERBESTIA_ID)).thenReturn(playerBestia)

    pbService = PlayerBestiaService(playerBestiaDao, attackLevelDao)
  }

  @Test
  fun getAllBestias_wrongAccId_empty() {
    val bestias = pbService.getAllBestias(WRONG_ACC_ID)
    assertThat(bestias, hasSize(0))
  }

  @Test
  fun getAllBestias_okAccId_allBestias() {
    val bestias = pbService.getAllBestias(OK_ACC_ID)

    assertThat(bestias, hasSize(ALL_BESTIAS.size))
  }

  @Test
  fun getPlayerBestia_wrongId_null() {
    val bestia = pbService.getPlayerBestia(WRONG_PLAYERBESTIA_ID)

    assertThat(bestia, nullValue())
  }

  @Test
  fun getPlayerBestia_okId_bestia() {
    val bestia = pbService.getPlayerBestia(OK_PLAYERBESTIA_ID)

    assertThat(bestia, notNullValue())
  }

  @Test
  fun getMaster_wrongAccId_null() {
    val master = pbService.getMaster(WRONG_ACC_ID)

    assertThat(master, nullValue())
  }

  @Test
  fun getMaster_okAccId_master() {
    val master = pbService.getMaster(OK_ACC_ID)

    assertThat(master, notNullValue())
  }

  @Test
  fun save_playerBestia_saved() {
    pbService.save(playerBestia)

    verify<PlayerBestiaRepository>(playerBestiaDao).save(playerBestia)
  }

  companion object {
    private const val WRONG_ACC_ID: Long = 2
    private const val OK_ACC_ID: Long = 1
    private val ALL_BESTIAS = ArrayList<PlayerBestia>()
    private const val OK_PLAYERBESTIA_ID: Long = 10
    private const val WRONG_PLAYERBESTIA_ID: Long = 11
  }
}
