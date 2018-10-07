package net.bestia.zoneserver.script

import org.junit.Assert
import org.junit.Before
import org.junit.Test

class ScriptPropertyAccessorTest {

  private var testAccess: TestOuter? = null
  private val accessor = ScriptPropertyAccessor(TestOuter::class.java)

  private class TestOuter {
    @ScriptProperty("ageChanged")
    var age: Int = 0

    @ScriptProperty
    var inner: TestInner? = null

    @get:ScriptProperty("renamed")
    val name = "Bruno"

    var notAnnotated = false
  }

  private class TestInner {

    @ScriptProperty
    var name: String? = null
  }

  @Before
  fun setup() {
    testAccess = TestOuter()
    testAccess!!.age = 16
    val inner = TestInner()
    testAccess!!.inner = inner
    inner.name = "Thomas"
    testAccess!!.notAnnotated = false
  }

  @Test
  fun set_NonBaseProperty_false() {
    Assert.assertFalse(accessor.set("inner", testAccess, 123))
    val newInner = TestInner()
    Assert.assertFalse(accessor.set("inner", testAccess, newInner))
  }

  @Test
  fun set_NonExistingKey_false() {
    Assert.assertFalse(accessor.set("notExisting", testAccess, 123))
  }

  @Test
  fun set_BaseProperty_true() {
    Assert.assertTrue(accessor.set("ageChanged", testAccess, 12))
    Assert.assertEquals(12, testAccess!!.age.toLong())
    Assert.assertFalse(accessor.set("age", testAccess, 10))
    Assert.assertEquals(12, testAccess!!.age.toLong())
    Assert.assertTrue(accessor.set("inner.name", testAccess, "test123"))
    Assert.assertEquals("test123", testAccess!!.inner!!.name)
    Assert.assertTrue(accessor.set("inner.name", testAccess, "#yolo"))
    Assert.assertEquals("#yolo", testAccess!!.inner!!.name)
  }

  @Test
  fun set_nonAnnotaed_false() {
    Assert.assertFalse(accessor.set("notAnnotated", testAccess, true))
    Assert.assertFalse(testAccess!!.notAnnotated)
  }

  @Test
  fun get_renamedGetter_returnsValue() {
    Assert.assertEquals("Bruno", accessor.get("renamed", testAccess))
  }

  @Test
  fun set_getterOnly_false() {
    Assert.assertFalse(accessor.set("renamed", testAccess, "Bla"))
    Assert.assertEquals("Bruno", testAccess!!.name)
  }

  @Test
  fun get_NonBaseProperty_null() {
    Assert.assertNull(accessor.get("inner", testAccess))
  }

  @Test
  fun get_NonExistingProperty_null() {
    Assert.assertNull(accessor.get("age123", testAccess))
  }

  @Test
  fun get_baseProperty_notNull() {
    Assert.assertEquals("Thomas", accessor.get("inner.name", testAccess))
  }
}
