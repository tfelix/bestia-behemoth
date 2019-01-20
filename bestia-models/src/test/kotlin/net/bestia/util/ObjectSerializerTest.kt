package net.bestia.util

import net.bestia.model.util.ObjectSerializer
import java.util.Arrays

import org.junit.Assert
import org.junit.Test

class ObjectSerializerTest {
  @Test
  fun deserialize_invalidData_null() {
    val invalidData = ByteArray(10)
    Arrays.fill(invalidData, 0x12.toByte())

    val test: String? = ObjectSerializer.deserialize(invalidData)
    Assert.assertNull(test)
  }

  @Test
  fun serializeAndDeserialize_validObject_ok() {
    val test = "Hello World"
    val data = ObjectSerializer.serialize(test)

    Assert.assertNotNull(data)
    Assert.assertTrue(data!!.isNotEmpty())

    val test2 = ObjectSerializer.deserialize<String>(data)

    Assert.assertEquals(test, test2)
  }
}
