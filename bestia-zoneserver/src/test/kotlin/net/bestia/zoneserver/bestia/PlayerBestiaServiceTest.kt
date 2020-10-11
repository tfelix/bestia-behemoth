package net.bestia.zoneserver.bestia

import net.bestia.model.account.Account
import net.bestia.model.account.AccountRepository
import net.bestia.model.battle.BestiaAttackRepository
import net.bestia.model.bestia.PlayerBestia
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.zoneserver.entity.PlayerBestiaService
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.util.*

@ExtendWith(MockitoExtension::class)
class PlayerBestiaServiceTest {

  private lateinit var pbService: PlayerBestiaService

  @Mock
  private lateinit var playerBestiaRepository: PlayerBestiaRepository

  @Mock
  private lateinit var attackLevelRepository: BestiaAttackRepository

  @Mock
  private lateinit var accountRepository: AccountRepository

  @Mock
  private lateinit var playerBestia: PlayerBestia

  private lateinit var account: Account

  @BeforeEach
  fun setup() {
    ALL_BESTIAS.clear()
    ALL_BESTIAS.add(playerBestia)

    account = Account.test().apply {
      playerBestias.add(playerBestia)
    }

    pbService = PlayerBestiaService(playerBestiaRepository, attackLevelRepository, accountRepository)
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

  companion object {
    private const val WRONG_ACC_ID: Long = 2
    private const val OK_ACC_ID: Long = 1
    private val ALL_BESTIAS = ArrayList<PlayerBestia>()
  }
}
