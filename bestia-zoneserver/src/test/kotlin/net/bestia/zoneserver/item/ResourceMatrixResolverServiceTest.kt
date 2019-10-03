package net.bestia.zoneserver.item

import net.bestia.model.item.CraftType
import net.bestia.model.item.Resource
import net.bestia.model.item.ResourceEntry
import net.bestia.model.item.ResourceMatrix
import org.junit.Assert
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class ResourceMatrixResolverServiceTest {
  @Test
  fun `similar items are in the same bucket`() {
    Assert.fail("finish test")
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

    private val resourceMatrices = makeTestData() + listOf(tableMatrix)

    fun makeTestData(): List<ResourceMatrix> {
      return (1..100).map {
        val matrix = ResourceMatrix(CraftType.FORGERY)
        val amountRes = (random.nextFloat() * 25).toInt()
        for (i in 0 until amountRes) {
          val row = i % 25
          val col = i / 25
          val type = Resource.values()[(random.nextFloat() * Resource.values().size).toInt()]
          val amount = random.nextInt(100)
          matrix.set(row, col, ResourceEntry(type, amount))
        }

        matrix
      }.toList()
    }
  }
}