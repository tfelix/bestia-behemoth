package net.bestia.zoneserver.item

import com.nhaarman.mockitokotlin2.whenever
import net.bestia.model.item.*
import org.junit.Assert
import org.junit.Before
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import kotlin.random.Random

@RunWith(MockitoJUnitRunner::class)
class ResourceMatrixResolverServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private lateinit var itemRepository: ItemRepository

  private lateinit var sut: ResourceMatrixResolverService

  @Before
  fun setup() {
    whenever(itemRepository.findAll()).thenReturn(testItems)

    sut = ResourceMatrixResolverService(itemRepository)
  }

  @Test
  fun `similar items are in the same bucket`() {
    sut.hashAllItems()

    val result = sut.resolveMatrix(tableMatrix)
    Assert.assertEquals(tableItemId, result)
  }

  companion object {
    private val random = Random(476)
    private val tableMatrix = ResourceMatrix(CraftType.FORGERY).apply {
      set(2, 2, ResourceEntry(Resource.WOOD, amount = 1))
      set(2, 1, ResourceEntry(Resource.WOOD, amount = 1))
      set(2, 0, ResourceEntry(Resource.WOOD, amount = 1))
      set(3, 0, ResourceEntry(Resource.WOOD, amount = 1))
      set(4, 0, ResourceEntry(Resource.WOOD, amount = 1))
      set(4, 1, ResourceEntry(Resource.WOOD, amount = 1))
      set(4, 2, ResourceEntry(Resource.WOOD, amount = 1))
    }

    private const val tableItemId = 10000L
    private val resourceMatrices = makeTestData() + listOf(tableMatrix)
    private val testItems = resourceMatrices.mapIndexed { i, m ->
      Item(
          itemDbName = "item-$i",
          mesh = "item-$i.mesh",
          type = ItemType.ETC
      ).apply {
        id = if (m == tableMatrix) {
          tableItemId
        } else {
          i.toLong()
        }
        recepies.add(CraftRecipe(m))
      }
    }

    private fun makeTestData(): List<ResourceMatrix> {
      return (1..100).map {
        val matrix = ResourceMatrix(CraftType.FORGERY)
        val amountRes = (random.nextFloat() * 25).toInt()
        for (i in 0 until amountRes) {
          val row = i % 5
          val col = i / 5
          val type = Resource.values()[(random.nextFloat() * Resource.values().size).toInt()]
          val amount = random.nextInt(100)
          matrix.set(row, col, ResourceEntry(type, amount))
        }

        matrix
      }.toList()
    }
  }
}