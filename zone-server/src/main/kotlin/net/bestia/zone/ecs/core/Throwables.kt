package net.bestia.zone.ecs.core

/**
 * True for errors after which the JVM cannot be trusted to keep simulating. A stack overflow is
 * excluded: it unwinds cleanly and is almost always one bad recursion in one system.
 */
fun Throwable.isFatal(): Boolean {
  return this is VirtualMachineError && this !is StackOverflowError
}
