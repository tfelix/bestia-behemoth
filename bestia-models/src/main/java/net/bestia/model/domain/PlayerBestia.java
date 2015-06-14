package net.bestia.model.domain;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Entity for the PlayerBestias these are bestias which are directly controlled by the player.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
@Entity
@Table(name="player_bestias")
@PrimaryKeyJoinColumn(name = "bestia_id")
public class PlayerBestia {
	@Transient
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private int id = 1;

	@JsonProperty("e")
	private int exp;
	
	@JsonProperty("cn")
	private String name = "";
	
	@AttributeOverrides({ @AttributeOverride(name = "mapDbName", column = @Column(name = "saveMapDbName")),
			@AttributeOverride(name = "x", column = @Column(name = "saveX")),
			@AttributeOverride(name = "y", column = @Column(name = "saveY")), })
	@Embedded
	@JsonProperty("sl")
	private Location savePosition;

	@Embedded
	@JsonProperty("cl")
	private Location currentPosition;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ACCOUNT_ID", nullable = false)
	@JsonIgnore
	private Account owner;

	@OneToOne(cascade = CascadeType.ALL, optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "BESTIA_ID", nullable = false)
	@JsonIgnore
	private Bestia originBestia;

	private int curHp;
	private int curMana;

	/**
	 * Override the names because the are the same like in status points. Both entities are embedded so we need
	 * individual column names. This values is added to each bestia when it kill another bestia from this kind.
	 */
	@Embedded
	@AttributeOverrides({ @AttributeOverride(name = "hp", column = @Column(name = "evHp")),
			@AttributeOverride(name = "mana", column = @Column(name = "evMana")),
			@AttributeOverride(name = "atk", column = @Column(name = "evAtk")),
			@AttributeOverride(name = "def", column = @Column(name = "evDef")),
			@AttributeOverride(name = "spAtk", column = @Column(name = "evSpAtk")),
			@AttributeOverride(name = "spDef", column = @Column(name = "evSpDef")),
			@AttributeOverride(name = "spd", column = @Column(name = "evSpd")) })
	@JsonIgnore
	private BaseValues effortValues;

	@Embedded
	@AttributeOverrides({ @AttributeOverride(name = "hp", column = @Column(name = "ivHp")),
			@AttributeOverride(name = "mana", column = @Column(name = "ivMana")),
			@AttributeOverride(name = "atk", column = @Column(name = "ivAtk")),
			@AttributeOverride(name = "def", column = @Column(name = "ivDef")),
			@AttributeOverride(name = "spAtk", column = @Column(name = "ivSpAtk")),
			@AttributeOverride(name = "spDef", column = @Column(name = "ivSpDef")),
			@AttributeOverride(name = "spd", column = @Column(name = "ivSpd")) })
	@JsonIgnore
	private BaseValues individualValue;
	
	public PlayerBestia() {
		initialize();
		this.individualValue = BaseValues.getNewIndividualValues();
	}
	
	public PlayerBestia(Account owner, Bestia origin) {
		if(owner == null) {
			throw new IllegalArgumentException("Owner can not be null.");
		}		
		if(origin == null) {
			throw new IllegalArgumentException("Origin Bestia can not be null.");
		}
		
		initialize();
		
		this.owner = owner;
		this.originBestia = origin;
		this.individualValue = BaseValues.getNewIndividualValues();
	}
	
	public PlayerBestia(Account owner, Bestia origin, BaseValues iValues) {
		if(owner == null) {
			throw new IllegalArgumentException("Owner can not be null.");
		}		
		if(origin == null) {
			throw new IllegalArgumentException("Origin Bestia can not be null.");
		}
		if(iValues == null) {
			throw new IllegalArgumentException("IValues can not be null.");
		}
		
		initialize();
		
		this.owner = owner;
		this.originBestia = origin;
		this.individualValue = iValues;
	}
	
	private void initialize() {
		Location defaultLocation = new Location("testmap124", 10, 10);	
		setCurrentPosition(defaultLocation);
		setSavePosition(defaultLocation);
		
		this.effortValues = new BaseValues();
		this.individualValue = BaseValues.getNewIndividualValues();
	}

	public int getExp() {
		return exp;
	}

	public void setExp(int exp) {
		this.exp = exp;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Location getSavePosition() {
		return savePosition;
	}

	public void setSavePosition(Location savePosition) {
		this.savePosition = savePosition;
	}

	public Location getCurrentPosition() {
		return currentPosition;
	}

	public void setCurrentPosition(Location currentPosition) {
		this.currentPosition = currentPosition;
	}

	public BaseValues getIndividualValue() {
		return individualValue;
	}

	public BaseValues getEffortValue() {
		return effortValues;
	}

	public int getId() {
		return id;
	}

	@JsonProperty("sp")
	public StatusPoints getStatusPoints() {
		return new StatusPoints();
	}

	public int getCurHp() {
		return curHp;
	}

	public void setCurHp(int curHp) {
		this.curHp = curHp;
	}

	public int getCurMana() {
		return curMana;
	}

	public void setCurMana(int curMana) {
		this.curMana = curMana;
	}
}
