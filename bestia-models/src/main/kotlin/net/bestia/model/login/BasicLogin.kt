package net.bestia.model.login

import net.bestia.model.AbstractEntity
import net.bestia.model.account.Account
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.ManyToOne
import javax.persistence.Table

/**
 * Saves information for basic authentication via password and email.
 */
@Entity
@Table(name = "basic_logins")
class BasicLogin(
    @Column(length = 64, unique = true, nullable = false)
    var email: String,
    var password: String,
    @ManyToOne
    var account: Account
) : AbstractEntity()
