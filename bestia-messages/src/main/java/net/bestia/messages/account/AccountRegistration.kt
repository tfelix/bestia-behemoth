package net.bestia.messages.account

import java.io.Serializable

import net.bestia.model.domain.Gender
import net.bestia.model.domain.Hairstyle
import net.bestia.model.domain.PlayerClass

/**
 * This POJO holds the data needed for an account registration.
 *
 * @author Thomas Felix
 */
class AccountRegistration : Serializable {

  var username: String? = null
  var email: String? = null
  var password: String? = null
  var gender: Gender? = null
  var hairstyle: Hairstyle? = null
  var campaignCode: String? = null
  var token: String? = null
  val playerClass = PlayerClass.KNIGHT

  companion object {

    private const val serialVersionUID = 1L
  }
}
