package net.bestia.model.web;

import java.io.Serializable;

import net.bestia.model.domain.Gender;
import net.bestia.model.domain.Hairstyle;

public class AccountRegistration implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private String username;
	private String email;
	private String password;
	private Gender gender;
	private Hairstyle hairstyle;
	private String campaignCode;
	private String authToken;

	public AccountRegistration() {
		
	}
}
