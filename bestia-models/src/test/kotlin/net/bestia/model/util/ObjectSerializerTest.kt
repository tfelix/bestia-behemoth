package net.bestia.model.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Arrays

class ObjectSerializerTest {
  @Test
  fun deserialize_invalidData_null() {
    val invalidData = ByteArray(10)
    Arrays.fill(invalidData, 0x12.toByte())

    val test: String? = ObjectSerializer.deserialize(invalidData)
    assertNull(test)
  }

  @Test
  fun serializeAndDeserialize_validObject_ok() {
    val test = "Hello World"
    val data = ObjectSerializer.serialize(test)

    assertNotNull(data)
    assertTrue(data!!.isNotEmpty())

    val test2 = ObjectSerializer.deserialize<String>(data)

    assertEquals(test, test2)
  }
}
