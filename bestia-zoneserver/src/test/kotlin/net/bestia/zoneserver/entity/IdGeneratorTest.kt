package net.bestia.zoneserver.entity

import net.bestia.zoneserver.Fixtures
import org.junit.Assert
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

internal class IdGeneratorTest {

  private val sut = IdGenerator(config = Fixtures.zoneserverNodeConfig)

  private class GetId(
      private val generator: IdGenerator
  ) : Callable<List<Long>> {
    override fun call(): List<Long> {
      return (0..999).map { generator.newId() }.toList()
    }
  }

  private val executor = Executors.newFixedThreadPool(4)

  @Test
  fun `newId produces different and valid ids in different threads`() {
    val results = (0..3)
        .map { GetId(sut) }
        .map { executor.submit(it) }
        .flatMap { it.get() }

    val duplicates = results.groupingBy { it }
        .eachCount()
        .filter { it.value > 1 }

    Assert.assertTrue("There are duplicate IDs", duplicates.isEmpty())
  }
}