package net.bestia.client.command

import net.bestia.client.BestiaSocketClient
import net.bestia.client.CLIException

class ConnectCommand(
  private val session: Session
) : CliCommand {
  override val name = "connect"
  override val usage = "connect [host] [port]"
  override val description = "Connects to a Bestia zone server. Automatically authenticates if JWT token is available."

  override fun execute(tokens: List<String>) {
    val (host, port) = when (tokens.size) {
      1 -> "localhost" to 8090
      3 -> {
        val portNum = tokens[2].toIntOrNull()
          ?: throw CLIException("Port must be a number")
        tokens[1] to portNum
      }
      else -> throw CLIException(usage)
    }

    connectClient(host, port)
  }

  private fun connectClient(host: String, port: Int) {
    session.client?.close()

    try {
      session.client = BestiaSocketClient(host, port, session)
      session.print("✓ Connected to $host:$port")

      // Automatically authenticate if JWT token is available
      if (session.isAuthenticated()) {
        session.print("Authenticating with zone server...")
        session.sendAuthenticatedEnvelope()
        session.print("✓ Authentication sent to zone server")
      } else {
        session.print("⚠️  Not authenticated. Use 'auth' command to authenticate with login server first.")
      }

    } catch (e: Exception) {
      throw CLIException("Connection failed: ${e.message}")
    }
  }
}