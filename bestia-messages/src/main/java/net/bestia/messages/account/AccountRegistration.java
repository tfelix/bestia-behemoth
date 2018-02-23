package net.bestia.messages.account;

import java.io.Serializable;

import bestia.model.domain.Gender;
import bestia.model.domain.Hairstyle;
import bestia.model.domain.PlayerClass;

/**
 * This POJO holds the data needed for an account registration.
 * 
 * @author Thomas Felix
 *
 */
public class AccountRegistration implements Serializable {

	private static final long serialVersionUID = 1L;

	private String username;
	private String email;
	private String password;
	private Gender gender;
	private Hairstyle hairstyle;
	private String campaignCode;
	private String token;
	private PlayerClass playerClass = PlayerClass.KNIGHT;

	public AccountRegistration() {

	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public Gender getGender() {
		return gender;
	}

	public void setGender(Gender gender) {
		this.gender = gender;
	}

	public Hairstyle getHairstyle() {
		return hairstyle;
	}

	public void setHairstyle(Hairstyle hairstyle) {
		this.hairstyle = hairstyle;
	}

	public String getCampaignCode() {
		return campaignCode;
	}

	public void setCampaignCode(String campaignCode) {
		this.campaignCode = campaignCode;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public PlayerClass getPlayerClass() {
		return playerClass;
	}
}
