package net.bestia.ai.blackboard

fun measureRuntime(fn: () -> Unit): Long {
  val start = System.currentTimeMillis()
  fn()
  val end = System.currentTimeMillis()

  return end - start
}