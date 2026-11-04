package net.bestia.zone.world

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/** Lets the large worlds go once the first class after the [ReadsLargeWorlds] group starts. */
class ReleaseLargeWorldsExtension : BeforeAllCallback {

  override fun beforeAll(context: ExtensionContext) {
    val testClass = context.requiredTestClass
    if (testClass.isAnnotationPresent(Nested::class.java) || testClass.isAnnotationPresent(ReadsLargeWorlds::class.java)) {
      return
    }

    GeneratedWorlds.releaseLarge()
  }
}
