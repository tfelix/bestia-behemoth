package net.bestia.zoneserver.battle

import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import net.bestia.model.bestia.BestiaRepository
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.model.bestia.StatusPointsImpl
import net.bestia.model.findOneOrThrow
import net.bestia.model.test.BestiaFixture
import net.bestia.model.test.PlayerBestiaFixture
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.PlayerComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
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
    whenever(bestiaDao.findById(bestiaId)).thenReturn(Optional.of(bestia))
    whenever(playerBestiaDao.findById(playerBestiaId))
        .thenReturn(Optional.of(PlayerBestiaFixture.playerBestiaWithoutMaster))

    statusService = StatusService(playerBestiaDao, bestiaDao)
  }

  @Test
  fun `non mob or player entity but with status values component calculates and uses level`() {
    val e = Entity(1)
    val statusComponent = StatusComponent(e.id)
    e.addAllComponents(listOf(
        statusComponent,
        LevelComponent(e.id, 10, 0)
    ))

    statusService.calculateStatusPoints(e)

    Assert.assertNotEquals(StatusPointsImpl(), statusComponent.originalStatusPoints)
  }

  @Test
  fun `mob tagged component calculates with mob data from database`() {
    val e = Entity(1)
    val statusComponent = StatusComponent(e.id)
    e.addAllComponents(listOf(
        statusComponent,
        LevelComponent(e.id, 10, 0)
    ))

    verify(bestiaDao).findOneOrThrow(bestiaId)
    Assert.assertNotEquals(StatusPointsImpl(), statusComponent.originalStatusPoints)
  }

  @Test
  fun `player component calculates with player data`() {
    val e = Entity(1)
    e.addComponent(PlayerComponent(
        entityId = e.id,
        playerBestiaId = playerBestiaId,
        ownerAccountId = 1
    ))

    statusService.calculateStatusPoints(e)
    verify(playerBestiaDao).findOneOrThrow(playerBestiaId)
  }
}