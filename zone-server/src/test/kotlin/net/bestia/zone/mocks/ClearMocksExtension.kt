package net.bestia.zone.mocks

import io.mockk.clearAllMocks
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Clears the mocks a test class made once the class is done, so the test instance and what its stubs captured
 * can be collected. MockK keeps every mock reachable until it is cleared; one stub answering with
 * `world.createEntity` held a whole ECS world for the rest of the run.
 *
 * Only this thread's mocks: a class runs on one thread, and other classes may still be running beside it.
 */
class ClearMocksExtension : AfterAllCallback {

  override fun afterAll(context: ExtensionContext) {
    // A nested class shares its thread with the enclosing class, whose mocks are still in use.
    if (context.requiredTestClass.isAnnotationPresent(Nested::class.java)) {
      return
    }

    clearAllMocks(currentThreadOnly = true)
  }
}
