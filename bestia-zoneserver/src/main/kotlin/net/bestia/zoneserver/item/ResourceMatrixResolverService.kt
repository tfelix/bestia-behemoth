package net.bestia.zoneserver.item

import info.debatty.java.lsh.LSHMinHash
import net.bestia.model.item.*
import kotlin.math.ceil
import kotlin.math.log2

class ResourceMatrixResolverService(
    private val itemRepository: ItemRepository
) {
  private val lsh = LSHMinHash(LSH_STAGES, (itemRepository.count() / 100).toInt(), TOTAL_ARRAY_SIZE, LSH_SEED)
  private var hashedItem: Map<CraftType, Map<Int, List<Long>>> = emptyMap()

  private fun Int.toBinaryString(): String {
    return String.format("%016d", Integer.parseInt(Integer.toBinaryString(this)));
  }

  fun resolveMatrix(matrix: ResourceMatrix): Long? {
    val buckets = hashedItem[matrix.craftType]
        ?: return null

    val vector = matrixToVector(matrix)
    val hash = lsh.hash(vector).last()

    val potentialItems = buckets[hash]
        ?: return null

    // TODO we must match the found items in this bucket in order to return the best matching one
    return potentialItems.first()
  }

  fun hashAllItems() {
    val result = mutableMapOf<CraftType, MutableMap<Int, MutableList<Long>>>()

    itemRepository
        .findAll()
        .forEach { item ->
          val vectors = item.recepies.map { Triple(it.recipe.craftType, matrixToVector(it.recipe), item) }
          vectors.forEach { (type, vector, item) ->
            val bucketNum = lsh.hash(vector).last()

            val entry = result.computeIfAbsent(type) {
              mutableMapOf()
            }
            val bucketItems = entry.computeIfAbsent(bucketNum) {
              mutableListOf()
            }
            bucketItems.add(item.id)
          }
        }

    hashedItem = result
  }

  private fun matrixToVector(recipe: ResourceMatrix): BooleanArray {
    val data = BooleanArray(TOTAL_ARRAY_SIZE)

    recipe.slots.forEachIndexed { i, entry ->
      if (entry == null) {
        "0".repeat(ARRAY_SLOT_SIZE)
      } else {
        val type = entry.resource.ordinal
        val amount = entry.amount


        val typeLength = nextExpOf2(Resource.values().size)
        val amountLength = nextExpOf2(ResourceEntry.MAX_RESOURCE_AMOUNT_PER_SLOT)

        var temp = type.toBinaryString().takeLast(typeLength)
        val typeBinary = "0".repeat(typeLength).take(typeLength - temp.length) + temp
        temp = amount.toBinaryString().takeLast(amountLength)
        val amountBinary = "0".repeat(amountLength).take(amountLength - temp.length) + temp

        val totalBinary = typeBinary + amountBinary
        totalBinary.forEachIndexed { j, c ->
          data[i * totalBinary.length + j] = when (c) {
            '0' -> false
            else -> true
          }
        }
      }
    }

    return data
  }

  companion object {
    private const val LSH_SEED = 5210587239752923754
    private const val LSH_STAGES = 5

    private fun nextExpOf2(n: Int): Int {
      return ceil(log2(n.toDouble())).toInt()
    }

    private val ARRAY_SLOT_SIZE = nextExpOf2(ResourceEntry.MAX_RESOURCE_AMOUNT_PER_SLOT) *
        nextExpOf2(Resource.values().size)
    private val TOTAL_ARRAY_SIZE = ResourceMatrix.MAX_SIZE * ResourceMatrix.MAX_SIZE * ARRAY_SLOT_SIZE

  }
}