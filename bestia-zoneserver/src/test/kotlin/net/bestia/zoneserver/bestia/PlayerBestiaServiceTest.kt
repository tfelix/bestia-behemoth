package net.bestia.zoneserver.bestia

import net.bestia.model.account.Account
import net.bestia.model.account.AccountRepository
import net.bestia.model.battle.BestiaAttackRepository
import net.bestia.model.bestia.PlayerBestiaDAO
import net.bestia.model.bestia.PlayerBestia
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.entity.PlayerBestiaService
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.util.*

@RunWith(MockitoJUnitRunner::class)
class PlayerBestiaServiceTest {

  private var pbService: PlayerBestiaService? = null

  @Mock
  private lateinit var accountDao: AccountRepository

  @Mock
  private lateinit var playerBestiaDao: PlayerBestiaDAO

  @Mock
  private lateinit var attackLevelDao: BestiaAttackRepository

  @Mock
  private lateinit var playerBestia: PlayerBestia

  @Mock
  private lateinit var account: Account

  @Before
  fun setup() {
    ALL_BESTIAS.clear()
    ALL_BESTIAS.add(playerBestia)

    `when`(accountDao.findOneOrThrow(OK_ACC_ID)).thenReturn(account)
    `when`(playerBestiaDao.findOneOrThrow(OK_PLAYERBESTIA_ID)).thenReturn(playerBestia)

    pbService = PlayerBestiaService(playerBestiaDao, attackLevelDao)
  }

  @Test
  fun getAllBestias_wrongAccId_empty() {
    val bestias = pbService!!.getAllBestias(WRONG_ACC_ID)
    assertThat(bestias, hasSize(0))
  }

  @Test
  fun getAllBestias_okAccId_allBestias() {
    val bestias = pbService!!.getAllBestias(OK_ACC_ID)

    assertThat(bestias, hasSize(ALL_BESTIAS.size))
  }

  @Test
  fun getPlayerBestia_wrongId_null() {
    val bestia = pbService!!.getPlayerBestia(WRONG_PLAYERBESTIA_ID)

    assertThat(bestia, nullValue())
  }

  @Test
  fun getPlayerBestia_okId_bestia() {
    val bestia = pbService!!.getPlayerBestia(OK_PLAYERBESTIA_ID)

    assertThat(bestia, notNullValue())
  }

  @Test
  fun getMaster_wrongAccId_null() {
    val master = pbService!!.getMaster(WRONG_ACC_ID)

    assertThat(master, nullValue())
  }

  @Test
  fun getMaster_okAccId_master() {
    val master = pbService!!.getMaster(OK_ACC_ID)

    assertThat(master, notNullValue())
  }

  @Test
  fun save_playerBestia_saved() {
    pbService!!.save(playerBestia)

    verify<PlayerBestiaDAO>(playerBestiaDao).save(playerBestia)
  }

  companion object {
    private const val WRONG_ACC_ID: Long = 2
    private const val OK_ACC_ID: Long = 1
    private val ALL_BESTIAS = ArrayList<PlayerBestia>()
    private const val OK_PLAYERBESTIA_ID: Long = 10
    private const val WRONG_PLAYERBESTIA_ID: Long = 11
  }
}
