package net.bestia.model.domain;

import java.io.Serializable;

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
 * Entity for the PlayerBestias these are bestias which are directly controlled
 * by the player.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
@Entity
@Table(name = "player_bestias")
@PrimaryKeyJoinColumn(name = "bestia_id")
public class PlayerBestia implements Serializable {
	@Transient
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private int id = 1;

	@JsonProperty("e")
	private int exp;

	@JsonProperty("cn")
	private String name = "";

	@AttributeOverrides({ @AttributeOverride(name = "mapDbName", column = @Column(name = "saveMapDbName") ),
			@AttributeOverride(name = "x", column = @Column(name = "saveX") ),
			@AttributeOverride(name = "y", column = @Column(name = "saveY") ), })
	@Embedded
	@JsonProperty("sl")
	private Location savePosition;

	/**
	 * The current hp value must be persisted inside the db. Since the status
	 * points are not persisted at all we need a certain field for it.
	 */
	@JsonIgnore
	private int currentHp;

	/**
	 * The current mana value must be persisted inside the db. Since the status
	 * points are not persisted at all we need a certain field for it.
	 */
	@JsonIgnore
	private int currentMana;

	@Embedded
	@JsonProperty("cl")
	private Location currentPosition;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "ACCOUNT_ID", nullable = false)
	private Account owner;

	@ManyToOne(optional = false, fetch = FetchType.EAGER)
	@JoinColumn(name = "BESTIA_ID", nullable = false)
	@JsonProperty("b")
	private Bestia originBestia;

	@JsonProperty("lv")
	private int level;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@JoinColumn(name = "ITEM_1", nullable = true)
	@JsonProperty("item1")
	private PlayerItem item1;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@JoinColumn(name = "ITEM_2", nullable = true)
	@JsonProperty("item2")
	private PlayerItem item2;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@JoinColumn(name = "ITEM_3", nullable = true)
	@JsonProperty("item3")
	private PlayerItem item3;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@JoinColumn(name = "ITEM_4", nullable = true)
	@JsonProperty("item4")
	private PlayerItem item4;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@JoinColumn(name = "ITEM_5", nullable = true)
	@JsonProperty("item5")
	private PlayerItem item5;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@JoinColumn(name = "ATTACK_1", nullable = true)
	@JsonProperty("atk1")
	private Attack attack1;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@JoinColumn(name = "ATTACK_2", nullable = true)
	@JsonProperty("atk2")
	private Attack attack2;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@JoinColumn(name = "ATTACK_3", nullable = true)
	@JsonProperty("atk3")
	private Attack attack3;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@JoinColumn(name = "ATTACK_4", nullable = true)
	@JsonProperty("atk4")
	private Attack attack4;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@JoinColumn(name = "ATTACK_5", nullable = true)
	@JsonProperty("atk5")
	private Attack attack5;

	/**
	 * Override the names because the are the same like in status points. Both
	 * entities are embedded so we need individual column names. This values is
	 * added to each bestia when it kill another bestia from this kind.
	 */
	@Embedded
	@AttributeOverrides({ @AttributeOverride(name = "hp", column = @Column(name = "evHp") ),
			@AttributeOverride(name = "mana", column = @Column(name = "evMana") ),
			@AttributeOverride(name = "atk", column = @Column(name = "evAtk") ),
			@AttributeOverride(name = "def", column = @Column(name = "evDef") ),
			@AttributeOverride(name = "spAtk", column = @Column(name = "evSpAtk") ),
			@AttributeOverride(name = "spDef", column = @Column(name = "evSpDef") ),
			@AttributeOverride(name = "spd", column = @Column(name = "evSpd") ) })
	@JsonIgnore
	private BaseValues effortValues;

	@Embedded
	@AttributeOverrides({ @AttributeOverride(name = "hp", column = @Column(name = "ivHp") ),
			@AttributeOverride(name = "mana", column = @Column(name = "ivMana") ),
			@AttributeOverride(name = "atk", column = @Column(name = "ivAtk") ),
			@AttributeOverride(name = "def", column = @Column(name = "ivDef") ),
			@AttributeOverride(name = "spAtk", column = @Column(name = "ivSpAtk") ),
			@AttributeOverride(name = "spDef", column = @Column(name = "ivSpDef") ),
			@AttributeOverride(name = "spd", column = @Column(name = "ivSpd") ) })
	@JsonIgnore
	private BaseValues individualValue;

	public PlayerBestia() {
		initialize();
		this.individualValue = BaseValues.getNewIndividualValues();
	}

	public PlayerBestia(Account owner, Bestia origin) {
		if (owner == null) {
			throw new IllegalArgumentException("Owner can not be null.");
		}
		if (origin == null) {
			throw new IllegalArgumentException("Origin Bestia can not be null.");
		}

		initialize();

		this.owner = owner;
		this.originBestia = origin;
		this.individualValue = BaseValues.getNewIndividualValues();
	}

	public PlayerBestia(Account owner, Bestia origin, BaseValues iValues) {
		if (owner == null) {
			throw new IllegalArgumentException("Owner can not be null.");
		}
		if (origin == null) {
			throw new IllegalArgumentException("Origin Bestia can not be null.");
		}
		if (iValues == null) {
			throw new IllegalArgumentException("IValues can not be null.");
		}

		initialize();

		this.owner = owner;
		this.originBestia = origin;
		this.individualValue = iValues;
	}

	private void initialize() {
		final Location defaultLocation = new Location("", 0, 0);
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

	@JsonIgnore
	public BaseValues getIndividualValue() {
		return individualValue;
	}

	@JsonIgnore
	public BaseValues getEffortValues() {
		return effortValues;
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	@JsonIgnore
	public Bestia getOrigin() {
		return originBestia;
	}

	@JsonIgnore
	public BaseValues getBaseValues() {
		return originBestia.getBaseValues();
	}

	@JsonIgnore
	public Account getOwner() {
		return owner;
	}

	public int getId() {
		return id;
	}

	@JsonIgnore
	public int getCurrentHp() {
		return currentHp;
	}

	public void setCurrentHp(int curHp) {
		this.currentHp = curHp;
	}

	@JsonIgnore
	public int getCurrentMana() {
		return currentMana;
	}

	public void setCurrentMana(int curMana) {
		this.currentMana = curMana;
	}

	public Attack getAttack1() {
		return attack1;
	}

	public void setAttack1(Attack attack1) {
		this.attack1 = attack1;
	}

	public Attack getAttack2() {
		return attack2;
	}

	public void setAttack2(Attack attack2) {
		this.attack2 = attack2;
	}

	public Attack getAttack3() {
		return attack3;
	}

	public void setAttack3(Attack attack3) {
		this.attack3 = attack3;
	}

	public Attack getAttack4() {
		return attack4;
	}

	public void setAttack4(Attack attack4) {
		this.attack4 = attack4;
	}

	public Attack getAttack5() {
		return attack5;
	}

	public void setAttack5(Attack attack5) {
		this.attack5 = attack5;
	}

	public PlayerItem getItem1() {
		return item1;
	}

	public void setItem1(PlayerItem item1) {
		this.item1 = item1;
	}

	public PlayerItem getItem2() {
		return item2;
	}

	public void setItem2(PlayerItem item2) {
		this.item2 = item2;
	}

	public PlayerItem getItem3() {
		return item3;
	}

	public void setItem3(PlayerItem item3) {
		this.item3 = item3;
	}

	public PlayerItem getItem4() {
		return item4;
	}

	public void setItem4(PlayerItem item4) {
		this.item4 = item4;
	}

	public PlayerItem getItem5() {
		return item5;
	}

	public void setItem5(PlayerItem item5) {
		this.item5 = item5;
	}

	@Override
	public int hashCode() {
		return Integer.hashCode(id);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PlayerBestia other = (PlayerBestia) obj;
		if (id != other.id)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return String.format("PlayerBestia[id: %d, name: %s, lv: %d, pos: %s]", id, name, level,
				currentPosition.toString());
	}
}
