package net.bestia.client.command

class StartCommand(
  session: Session
) : CliCommand {
  override val name = "start"
  override val description =
    "Convenient shortcut to connect to the default server, authenticate, list available bestias and select the main bestia."
  override val usage = "start"

  private val connectCommand = ConnectCommand(session)
  private val authCommand = AuthCommand(session)

  override fun execute(tokens: List<String>) {
    connectCommand.execute(listOf("connect"))
    authCommand.execute(listOf("auth"))
  }
}