package net.bestia.model;

import java.text.MessageFormat;
import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.Column;

@Entity
@Table(name = "account")
public class Account {

	@Transient
	public final static int MAX_MASTER_LEVEL = 60;
	@Transient
	public final static int MAX_BESTIA_SLOTS = 6;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	@Column(length = 32, unique = true)
	private String email = "";
	@Embedded
	private Password password;
	private int additionalBestiaSlots = 0;
	private int gold = 0;
	private String loginToken = "";
	private Date registerDate;
	private Date lastLogin;
	private boolean isActivated = false;
	private String remarks = "";
	private Date bannedUntilDate;

	// @OneToMany(mappedBy="account")
	// private List<GuildMember> guild;

	//@OneToOne(cascade = CascadeType.ALL, optional = true)
	//@JoinColumn(name = "MASTER_ID", nullable = true)
	//private PlayerBestia master;

	//@OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "owner")
	//private List<PlayerBestia> bestias;

	public Account() {
		setRegisterDate(new Date());
		password = new Password();
	}
	
	public Account(String email, String password) {
		this.email = email;
		this.password = new Password(password);
		
	}

	public long getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public Password getPassword() {
		return password;
	}

	public void setPassword(Password password) {
		this.password = password;
	}
/*
	public PlayerBestia getMaster() {
		return master;
	}

	/*
	 * public void setMaster(PlayerBestiaData master) { this.master = master; }
	 * 
	 * public List<PlayerBestiaData> getBestias() { return java.util.Collections.unmodifiableList(bestias); }
	 * 
	 * public void addBestia(PlayerBestiaData bestia) { if(bestias.size() + 1 >= bestiaSlots + additionalBestiaSlots) {
	 * throw new IllegalArgumentException("Number of bestias can not exceed the slots + the additional slots."); }
	 * this.bestias.add(bestia); }
	 * 
	 * public void removeBestia(PlayerBestiaData bestia) { this.bestias.remove(bestia); }
	 */

	public int getAdditionalBestiaSlots() {
		return additionalBestiaSlots;
	}

	public void setAdditionalBestiaSlots(int additionalBestiaSlots) {
		this.additionalBestiaSlots = additionalBestiaSlots;
	}

	public int getGold() {
		return gold;
	}

	public void setGold(int gold) {
		if (gold < 0) {
			throw new IllegalArgumentException("Gold value can not be negative.");
		}
		this.gold = gold;
	}

	public Date getBannedUntilDate() {
		return bannedUntilDate;
	}

	public void setBannedUntilDate(Date bannedUntilDate) {
		this.bannedUntilDate = bannedUntilDate;
	}

	public Date getRegisterDate() {
		return registerDate;
	}

	public void setRegisterDate(Date registerDate) {
		this.registerDate = registerDate;
	}

	public Date getLastLogin() {
		return lastLogin;
	}

	public void setLastLogin(Date lastLogin) {
		this.lastLogin = lastLogin;
	}

	public boolean isActivated() {
		return isActivated;
	}

	public void setActivated(boolean isActivated) {
		this.isActivated = isActivated;
	}

	public String getLoginToken() {
		return loginToken;
	}

	public void setLoginToken(String token) {
		this.loginToken = token;
	}

	public String getRemarks() {
		return remarks;
	}

	public void setRemarks(String remarks) {
		this.remarks = remarks;
	}
/*
	public List<PlayerBestia> getBestias() {
		return bestias;
	}*/
	
	@Override
	public int hashCode() {
		final int prime = 31;
        int result = 1;
        result = prime * result
                + ((email == null) ? 0 : email.hashCode());
        result = prime * result + (int)id;
        // TODO Alle anderen Felder hashen.
        return result;
	}
	
	public boolean equals(Object obj) {
		if(this == obj) {
			return true;
		}
		if(obj == null || !(obj instanceof Account)) {
			return false;
		}
		
		Account other = (Account) obj;
		
		if(id != other.id) {
			return false;
		}
		
		// TODO Alle anderen Felder prüfen.
		
		return true;
	}
	
	@Override
	public String toString() {
		return String.format("Account[id=%d, email=%s, registerDate=%t]", id, email, registerDate);
	}

}
