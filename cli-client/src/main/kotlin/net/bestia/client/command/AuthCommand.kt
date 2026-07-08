package net.bestia.client.command

import net.bestia.client.CLIException
import net.bestia.client.auth.AuthenticationRestClient
import net.bestia.client.auth.StaticAuthResult
import net.bestia.client.preferences.UserPreferencesService
import java.io.IOException

class AuthCommand(
  private val session: Session
) : CliCommand {
  override val name = "auth"
  override val usage = "auth [username] [staticToken] [loginServerUrl]"
  override val description = "Authenticate with the login server using a static dev token."

  private val userPreferences = UserPreferencesService.instance

  override fun execute(tokens: List<String>) {
    try {
      val username = if (tokens.size >= 2) tokens[1] else "user"
      val staticToken = if (tokens.size >= 3) tokens[2] else "dev-user-token"
      val loginServerUrl = if (tokens.size >= 4) tokens[3] else "http://localhost:8080"

      session.print("Authenticating as '$username' with login server at $loginServerUrl...")
      val restClient = AuthenticationRestClient(loginServerUrl)

      when (val response = restClient.authenticateStatic(username, staticToken)) {
        is StaticAuthResult.Success -> {
          userPreferences.setPreference(Session.PREF_JWT_TOKEN, response.token)

          session.print("✓ Authentication successful!")
          session.print("JWT token received and stored.")
        }

        is StaticAuthResult.Failure -> {
          throw CLIException("Authentication failed: ${response.error}")
        }
      }
    } catch (e: IOException) {
      throw CLIException("Network error: ${e.message}")
    } catch (e: Exception) {
      throw CLIException("Authentication error: ${e.message}")
    }
  }
}
