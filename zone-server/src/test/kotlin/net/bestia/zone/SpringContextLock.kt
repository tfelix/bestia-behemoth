package net.bestia.zone

/**
 * The JUnit resource lock every Spring test class holds. They share one context, and with it one world, one
 * tick and one database, so they run one at a time while plain unit test classes run beside them.
 */
object SpringContextLock {
  const val NAME = "zone-spring-context"
}
