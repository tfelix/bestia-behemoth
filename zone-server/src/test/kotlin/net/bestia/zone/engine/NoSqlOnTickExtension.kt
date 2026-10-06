package net.bestia.zone.engine

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.test.assertEquals

/**
 * Fails a test class when the tick ran SQL while it ran. The guard's exception alone may not: a system that
 * throws is only logged, so the tick goes on.
 */
class NoSqlOnTickExtension : BeforeAllCallback, AfterAllCallback {

  override fun beforeAll(context: ExtensionContext) {
    context.getStore(NAMESPACE).put(BASELINE, TickSqlGuard.violations.get())
  }

  override fun afterAll(context: ExtensionContext) {
    val baseline = context.getStore(NAMESPACE).get(BASELINE, Int::class.javaObjectType)

    assertEquals(baseline, TickSqlGuard.violations.get(), "SQL ran on the tick, last: ${TickSqlGuard.lastViolation}")
  }

  private companion object {
    val NAMESPACE: ExtensionContext.Namespace = ExtensionContext.Namespace.create(NoSqlOnTickExtension::class.java)
    const val BASELINE = "violations"
  }
}
