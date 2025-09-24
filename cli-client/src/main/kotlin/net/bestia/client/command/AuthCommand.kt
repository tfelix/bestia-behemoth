package net.bestia.client.command

import net.bestia.client.CLIException
import net.bestia.client.auth.AuthenticationRestClient
import net.bestia.client.preferences.UserPreferencesService
import net.bestia.login.eip712.Eip712AuthRequest
import net.bestia.login.eip712.Eip712AuthResponse
import java.io.IOException

class AuthCommand(
  private val session: Session
) : CliCommand {
  override val name = "auth"
  override val usage = "auth [tokenIndex] [loginServerUrl]"
  override val description = "Authenticate using EIP712 signature with your wallet. Creates wallet if none exists."

  private val userPreferences = UserPreferencesService.instance

  override fun execute(tokens: List<String>) {
    try {
      val refreshToken = if(userPreferences.hasPreference(PREF_JWT_REFRESH)) {
        session.print("JWT refresh token found, skipping auth")
        userPreferences.getPreference(PREF_JWT_REFRESH)
      } else {
        fetchRefreshToken(tokens)
        userPreferences.getPreference(PREF_JWT_REFRESH)
      }

      // Fetch login token with refresh token

      // if refresh token is expired, try to fetch a new token.

      // open a connection and present the login token

    } catch (e: IOException) {
      throw CLIException("Network error: ${e.message}")
    } catch (e: NumberFormatException) {
      throw CLIException("Invalid number format: ${e.message}")
    } catch (e: Exception) {
      throw CLIException("Authentication error: ${e.message}")
    }
  }

  private fun fetchRefreshToken(tokens: List<String>) {
    val tokenIndex = if (tokens.size >= 2) {
      tokens[1].toLongOrNull() ?: throw CLIException("Invalid tokenIndex: ${tokens[1]}")
    } else {
      1L // default token index
    }

    val loginServerUrl = if (tokens.size >= 3) {
      tokens[2]
    } else {
      "http://localhost:8080" // default login server URL
    }

    session.print("Loading wallet...")
    val walletInfo = walletManager.loadOrCreateWallet()
    session.print("Using wallet: ${walletInfo.address}")

    session.print("Generating EIP712 signature...")
    val signature = eip712Service.generateSignature(
      privateKey = walletInfo.privateKey,
      wallet = walletInfo.address,
      tokenIndex = tokenIndex
    )

    val authRequest = Eip712AuthRequest(
      wallet = walletInfo.address,
      tokenIndex = tokenIndex,
      signature = signature
    )

    session.print("Authenticating with login server at $loginServerUrl...")
    val restClient = AuthenticationRestClient(loginServerUrl)

    when (val response = restClient.authenticate(authRequest)) {
      is Eip712AuthResponse.Success -> {
        // Save the refresh token now.
        userPreferences.setPreference(PREF_JWT_REFRESH, response.token)

        session.print("✓ Authentication successful!")
        session.print("JWT refresh received and stored.")
      }

      is Eip712AuthResponse.Failure -> {
        throw CLIException("Authentication failed: ${response.error}")
      }
    }
  }

  companion object {
    private const val PREF_JWT_REFRESH = "jwtRefreshToken"
  }
}