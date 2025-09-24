package net.bestia.client.command

interface CliCommand {
  val name: String
  val usage: String
  val description: String

  fun execute(tokens: List<String>)
}