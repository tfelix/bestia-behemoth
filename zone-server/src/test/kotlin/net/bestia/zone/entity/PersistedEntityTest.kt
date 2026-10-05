package net.bestia.zone.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PersistedEntityTest {

  @Test
  fun `writing a blob again updates the row it already has`() {
    val entity = PersistedEntity(entityId = 1L, kind = "mob")
    entity.writeComponent("mob", "{\"hp\":10}")
    val first = entity.components.single()

    entity.writeComponent("mob", "{\"hp\":7}")

    assertSame(first, entity.components.single(), "a new row would be a DELETE and an INSERT")
    assertEquals("{\"hp\":7}", first.data)
  }

  @Test
  fun `a blob of another type is replaced`() {
    val entity = PersistedEntity(entityId = 1L, kind = "mob")
    entity.writeComponent("old", "{}")

    entity.writeComponent("mob", "{\"hp\":10}")

    assertEquals(listOf("mob"), entity.components.map { it.type })
  }
}
