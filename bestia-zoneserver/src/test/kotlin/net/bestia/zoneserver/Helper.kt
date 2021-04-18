package net.bestia.zoneserver

import org.junit.jupiter.api.Assertions

inline fun <reified T : Any> assertInstanceOf(value: Any): T {
  if (value !is T) {
    Assertions.fail<Any>("Object was unexcepted type")
  }
  return value as T
}