package net.bestia.loginserver.account

import net.bestia.model.account.Gender
import net.bestia.model.account.Hairstyle

data class AccountCreateModel(
    val username: String,
    val gender: Gender,
    val hairstyle: Hairstyle,
    val hairColor: String,
    val skinColor: String,
    val playerMaster: Int,
    val promoCode: String? = null,
    val basicLogin: BasicLoginCreateModel
)
