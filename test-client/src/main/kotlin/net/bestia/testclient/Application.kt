package net.bestia.testclient

import mu.KotlinLogging

private val LOG = KotlinLogging.logger { }

fun main() {
  LOG.info { "Bestia CLI Client" }
  var input = ""
  do {
    print("> ")
    input = readLine()!!
  } while (input != "q")
}