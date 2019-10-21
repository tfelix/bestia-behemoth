package net.bestia.testclient

import net.bestia.messages.AuthMessageProto

class LoginCommand {
  val commandName = "login"

  fun execute(): ByteArray {
    return AuthMessageProto.AuthMessage.newBuilder()
        .setAccountId(1)
        .setToken(LOGIN_TOKEN)
        .build()
        .toByteArray()
  }

  companion object {
    private const val LOGIN_TOKEN = "50cb5740-c390-4d48-932f-eef7cbc113c1"
  }
}