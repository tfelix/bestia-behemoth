package net.bestia.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Entity
@Table(name = "accounts")
public class Account implements Serializable {

	public enum UserLevel {
		USER, GM, SUPER_GM, ADMIN
	}

	@Transient
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$");
	@Transient
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(unique = true, nullable = false)
	private long id = 0;

	@Column(length = 64, unique = true, nullable = false)
	private String email = "";

	@Embedded
	private Password password;

	private int additionalBestiaSlots = 0;
	private int gold = 0;
	private String loginToken = "";

	@Temporal(TemporalType.DATE)
	private Date registerDate;

	@Temporal(TemporalType.DATE)
	private Date lastLogin;

	private boolean isActivated = false;

	private String remarks = "";

	@Column(nullable = false)
	private String language = "en";

	@Temporal(TemporalType.DATE)
	private Date bannedUntilDate;
	
	@Enumerated(EnumType.STRING)
	private Gender gender; 

	@Enumerated(EnumType.STRING)
	private UserLevel userLevel = UserLevel.USER;
	
	private Hairstyle hairstyle = Hairstyle.female_01;
	
	@ManyToOne(cascade = CascadeType.ALL)
	private Party party;

	// @OneToMany(mappedBy="account")
	// private List<GuildMember> guild;

	@OneToMany(cascade = CascadeType.ALL, mappedBy = "account", fetch = FetchType.LAZY)
	private Set<PlayerItem> items = new HashSet<>(0);

	@OneToOne(cascade = CascadeType.ALL, mappedBy = "master")
	private PlayerBestia master;

	@OneToMany(cascade = CascadeType.ALL, mappedBy = "owner")
	private List<PlayerBestia> bestias = new ArrayList<>();

	public Account() {
		this.email = "";
		this.password = new Password();
		setRegisterDate(new Date());
		setLastLogin(new Date());
		setBannedUntilDate(new Date());
	}

	public Account(String email, String password) {
		if (email == null || email.isEmpty()) {
			throw new IllegalArgumentException("Email can not be null or empty.");
		}
		if (password == null || password.isEmpty()) {
			throw new IllegalArgumentException("Password can not be null or empty.");
		}
		final Matcher m = EMAIL_PATTERN.matcher(email);
		if (!m.matches()) {
			throw new IllegalArgumentException("Email is not valid: " + email);
		}

		this.email = email;
		this.password = new Password(password);
		setRegisterDate(new Date());
		setLastLogin(new Date());
		setBannedUntilDate(new Date());
	}

	public long getId() {
		return id;
	}

	public void setId(Long id) {
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

	public List<PlayerBestia> getBestias() {
		return java.util.Collections.unmodifiableList(bestias);
	}

	public int getAdditionalBestiaSlots() {
		return additionalBestiaSlots;
	}

	public void setAdditionalBestiaSlots(int additionalBestiaSlots) {
		this.additionalBestiaSlots = additionalBestiaSlots;
	}
	
	public Hairstyle getHairstyle() {
		return hairstyle;
	}
	
	public void setHairstyle(Hairstyle hairstyle) {
		this.hairstyle = hairstyle;
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
		return (Date) bannedUntilDate.clone();
	}

	public void setBannedUntilDate(Date bannedUntilDate) {
		this.bannedUntilDate = (Date) bannedUntilDate.clone();
	}

	public Date getRegisterDate() {
		return (Date) registerDate.clone();
	}

	public void setRegisterDate(Date registerDate) {
		this.registerDate = (Date) registerDate.clone();
	}

	public Date getLastLogin() {
		return (Date) lastLogin.clone();
	}

	public void setLastLogin(Date lastLogin) {
		this.lastLogin = (Date) lastLogin.clone();
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

	@JsonIgnore
	public PlayerBestia getMaster() {
		return master;
	}

	public void setMaster(PlayerBestia masterBestia) {
		if (masterBestia == null) {
			throw new IllegalArgumentException("MasterBestia can not be null.");
		}
		this.master = masterBestia;
	}

	public UserLevel getUserLevel() {
		return userLevel;
	}

	public void setUserLevel(UserLevel userLevel) {
		this.userLevel = userLevel;
	}
	
	public Gender getGender() {
		return gender;
	}
	
	public void setGender(Gender gender) {
		this.gender = gender;
	}

	@Override
	public int hashCode() {
		return email.hashCode();
	}

	/**
	 * Returns the username of the account, the name of the bestia master.
	 * 
	 * @return
	 */
	public String getName() {
		if (master == null) {
			return "";
		}
		return master.getName();
	}

	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || !(obj instanceof Account)) {
			return false;
		}

		final Account other = (Account) obj;
		return email.equals(other.email);
	}

	@Override
	public String toString() {
		final String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(registerDate);
		return String.format("Account[id: %d, email: %s, registerDate: %s]", id, email, dateStr);
	}

	public Locale getLanguage() {
		return new Locale(language);
	}

	public void setLanguage(Locale locale) {
		this.language = locale.getLanguage();
	}
}
