package net.bestia.testclient.commands

interface Command {
  fun matches(line: String): Boolean
  fun execute(line: String): ByteArray
}