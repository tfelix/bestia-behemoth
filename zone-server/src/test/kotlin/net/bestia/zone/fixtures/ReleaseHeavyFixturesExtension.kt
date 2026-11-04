package net.bestia.zone.fixtures

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/** Lets every [HeavyFixture] go that the starting class does not read: the group of that fixture is over. */
class ReleaseHeavyFixturesExtension : BeforeAllCallback {

  override fun beforeAll(context: ExtensionContext) {
    val testClass = context.requiredTestClass
    if (testClass.isAnnotationPresent(Nested::class.java)) {
      return
    }

    val stillRead = HeavyFixture.readBy(testClass)
    HeavyFixture.entries.filter { it != stillRead }.forEach { it.release() }
  }
}
