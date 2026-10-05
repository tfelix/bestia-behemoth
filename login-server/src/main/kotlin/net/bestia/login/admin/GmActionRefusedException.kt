package net.bestia.login.admin

class GmActionRefusedException(
  val refusal: Refusal,
  message: String
) : RuntimeException(message) {

  enum class Refusal {
    NOT_ALLOWED,
    NO_SUCH_ACCOUNT
  }
}
