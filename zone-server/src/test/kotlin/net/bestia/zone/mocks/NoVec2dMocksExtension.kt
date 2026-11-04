package net.bestia.zone.mocks

import net.bestia.worldgen.vector.Vec2d
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.lang.reflect.InvocationTargetException
import java.util.WeakHashMap

/**
 * Fails a test class that made MockK rewrite [Vec2d]. MockK rewrites a final class it proxies for the rest of the
 * JVM, and a rewritten `Vec2d` makes every chunk about 14 times slower to generate - in every later test, and on
 * the shared Spring context's tick. Stubbing any call that takes or returns a `Vec2d` is enough: stub a real
 * object's collaborator instead, or give the fake real values.
 *
 * MockK has no public API for this, so it reads the agent's per-class proxy count. A MockK upgrade that moves it
 * fails every class here, loudly, rather than letting the guard go quiet.
 */
class NoVec2dMocksExtension : BeforeAllCallback, AfterAllCallback {

  override fun beforeAll(context: ExtensionContext) {
    if (isNested(context)) {
      return
    }

    context.getStore(NAMESPACE).put(PROXIES_BEFORE, vec2dProxyCount())
  }

  override fun afterAll(context: ExtensionContext) {
    if (isNested(context)) {
      return
    }

    val before = context.getStore(NAMESPACE).get(PROXIES_BEFORE, Int::class.javaObjectType) ?: return
    if (vec2dProxyCount() > before) {
      throw AssertionError(
        "${context.requiredTestClass.simpleName} made MockK rewrite Vec2d, which slows every later test. " +
          "See NoVec2dMocksExtension."
      )
    }
  }

  private fun isNested(context: ExtensionContext): Boolean {
    return context.requiredTestClass.isAnnotationPresent(Nested::class.java)
  }

  private companion object {
    val NAMESPACE: ExtensionContext.Namespace = ExtensionContext.Namespace.create(NoVec2dMocksExtension::class.java)
    const val PROXIES_BEFORE = "vec2d proxies before"

    /** Gateway, agent factory, proxy maker, inliner, spec map: where MockK 1.14 keeps a count per rewritten class. */
    fun vec2dProxyCount(): Int {
      val gateway = gatewayOrNull() ?: return 0
      val specMap = listOf("agentFactory", "jvmProxyMaker", "inliner", "specMap").fold(gateway) { owner, name ->
        fieldOf(owner, name)
      }
      val specs = fieldOf(specMap, "classSpecs") as WeakHashMap<*, *>
      val spec = synchronized(specs) { specs[Vec2d::class.java] } ?: return 0
      return fieldOf(spec, "simpleIntercept") as Int
    }

    /** Null until the JVM's first mock: MockK sets its gateway up lazily, and nothing is rewritten before that. */
    fun gatewayOrNull(): Any? {
      val companion = Class.forName("io.mockk.MockKGateway").getDeclaredField("Companion").get(null)
      try {
        return (companion.javaClass.getMethod("getImplementation").invoke(companion) as () -> Any)()
      } catch (e: InvocationTargetException) {
        if (e.cause is UninitializedPropertyAccessException) {
          return null
        }
        throw e
      }
    }

    fun fieldOf(owner: Any, name: String): Any {
      var type: Class<*>? = owner.javaClass
      while (type != null) {
        val field = type.declaredFields.firstOrNull { it.name == name }
        if (field != null) {
          field.isAccessible = true
          return field.get(owner)
        }
        type = type.superclass
      }
      throw IllegalStateException("MockK no longer has '$name' on ${owner.javaClass.name}; update NoVec2dMocksExtension")
    }
  }
}
