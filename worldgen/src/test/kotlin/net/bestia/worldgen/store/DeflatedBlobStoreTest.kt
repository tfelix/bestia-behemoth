package net.bestia.worldgen.store

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeflatedBlobStoreTest {

  @Test
  fun `a framed blob unframes to itself, deflated when that is smaller`() {
    val repetitive = ByteArray(4096) { (it % 7).toByte() }

    val framed = DeflatedBlobStore.frame(repetitive)

    assertTrue(framed.size < repetitive.size / 4)
    assertContentEquals(repetitive, DeflatedBlobStore.unframe("blob", framed))
  }

  @Test
  fun `a small blob is stored as it is`() {
    val small = byteArrayOf(4, 2)

    val framed = DeflatedBlobStore.frame(small)

    assertContentEquals(byteArrayOf(0, 4, 2), framed)
    assertContentEquals(small, DeflatedBlobStore.unframe("blob", framed))
  }

  @Test
  fun `an unknown framing byte is refused`() {
    assertFailsWith<IllegalStateException> { DeflatedBlobStore.unframe("blob", byteArrayOf(7, 1, 2)) }
  }
}
