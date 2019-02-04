package net.bestia.zoneserver.battle

import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import net.bestia.model.bestia.BestiaRepository
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.model.bestia.StatusPointsImpl
import net.bestia.model.test.BestiaFixture
import net.bestia.model.test.PlayerBestiaFixture
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.MetaDataComponent
import net.bestia.zoneserver.entity.component.PlayerComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.util.*

@ExtendWith(MockitoExtension::class)
class StatusServiceTest {

  private lateinit var statusService: StatusService

  @Mock
  private lateinit var playerBestiaDao: PlayerBestiaRepository

  @Mock
  private lateinit var bestiaDao: BestiaRepository

  private val bestiaId = 1L
  private val playerBestiaId = 2L
  private val bestia = BestiaFixture.bestia

  @BeforeEach
  fun setup() {
    statusService = StatusService(playerBestiaDao, bestiaDao)
  }

  @Disabled("Currently no status calc for non player or mob entities")
  @Test
  fun `non mob or player entity but with status values component calculates and uses level`() {
    val e = Entity(1)
    val statusComponent = StatusComponent(e.id)
    e.addAllComponents(listOf(
        statusComponent,
        LevelComponent(e.id, 10, 0)
    ))

    val resultStatus = statusService.calculateStatusPoints(e)

    Assert.assertNotEquals(StatusPointsImpl(), resultStatus.originalStatusPoints)
  }

  @Test
  fun `mob tagged component calculates with mob data from database`() {
    whenever(bestiaDao.findById(bestiaId))
        .thenReturn(Optional.of(BestiaFixture.bestia))

    val e = Entity(1)
    val statusComponent = StatusComponent(e.id)
    e.addAllComponents(listOf(
        statusComponent,
        LevelComponent(e.id, 10, 0),
        MetaDataComponent(e.id, mapOf(
            MetaDataComponent.MOB_BESTIA_ID to bestiaId.toString()
        ))
    ))
    val resultStatus = statusService.calculateStatusPoints(e)

    verify(bestiaDao).findById(bestiaId)
    Assert.assertNotEquals(StatusPointsImpl(), resultStatus.originalStatusPoints)
  }

  @Test
  fun `player component calculates with player data`() {
    whenever(playerBestiaDao.findById(playerBestiaId))
        .thenReturn(Optional.of(PlayerBestiaFixture.playerBestiaWithoutMaster))

    val e = Entity(1)
    e.addComponent(PlayerComponent(
        entityId = e.id,
        playerBestiaId = playerBestiaId,
        ownerAccountId = 1
    ))
    e.addComponent(StatusComponent(
        entityId = e.id
    ))

    statusService.calculateStatusPoints(e)
    verify(playerBestiaDao).findById(playerBestiaId)
  }
}