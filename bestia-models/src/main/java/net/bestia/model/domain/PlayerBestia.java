package net.bestia.model.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
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
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import net.bestia.model.geometry.Point;

/**
 * Entity for the PlayerBestias these are bestias which are directly controlled
 * by the player.
 * 
 * @author Thomas Felix
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
	private long id;

	@JsonProperty("e")
	private int exp;

	@JsonProperty("cn")
	private String name;

	@AttributeOverrides({
			@AttributeOverride(name = "x", column = @Column(name = "saveX")),
			@AttributeOverride(name = "y", column = @Column(name = "saveY")), })
	@Embedded
	@JsonProperty("sl")
	private Point savePosition = new Point(0, 0);

	@Embedded
	@JsonUnwrapped
	private StatusValues statusValues;

	@Embedded
	@JsonProperty("cl")
	private Point currentPosition = new Point(0, 0);

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "ACCOUNT_ID", nullable = false)
	private Account owner;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "MASTER_ID", nullable = true, unique = true)
	@JsonIgnore
	private Account master;

	@ManyToOne(optional = false, fetch = FetchType.EAGER)
	@JoinColumn(name = "BESTIA_ID", nullable = false)
	@JsonProperty("b")
	private Bestia originBestia;

	@JsonProperty("lv")
	private int level;

	@JsonIgnore
	private long entityId;

	/**
	 * Override the names because the are the same like in status points. Both
	 * entities are embedded so we need individual column names. This values is
	 * added to each bestia when it kill another bestia from this kind.
	 */
	@Embedded
	@AttributeOverrides({ @AttributeOverride(name = "hp", column = @Column(name = "evHp")),
			@AttributeOverride(name = "mana", column = @Column(name = "evMana")),
			@AttributeOverride(name = "strength", column = @Column(name = "evStr")),
			@AttributeOverride(name = "defense", column = @Column(name = "evDef")),
			@AttributeOverride(name = "intelligence", column = @Column(name = "evInt")),
			@AttributeOverride(name = "willpower", column = @Column(name = "evWill")),
			@AttributeOverride(name = "agility", column = @Column(name = "evAgi")),
			@AttributeOverride(name = "dexterity", column = @Column(name = "evDex")) })
	@JsonIgnore
	private BaseValues effortValues;

	@Embedded
	@AttributeOverrides({ @AttributeOverride(name = "hp", column = @Column(name = "ivHp")),
			@AttributeOverride(name = "mana", column = @Column(name = "ivMana")),
			@AttributeOverride(name = "strength", column = @Column(name = "ivAtk")),
			@AttributeOverride(name = "vitality", column = @Column(name = "ivDef")),
			@AttributeOverride(name = "intelligence", column = @Column(name = "ivSpAtk")),
			@AttributeOverride(name = "willpower", column = @Column(name = "ivSpDef")),
			@AttributeOverride(name = "agility", column = @Column(name = "ivSpd")),
			@AttributeOverride(name = "dexterity", column = @Column(name = "ivDex")) })
	@JsonIgnore
	private BaseValues individualValue;

	public PlayerBestia() {

		initialize();
		this.individualValue = BaseValues.getNewIndividualValues();
	}

	public PlayerBestia(Account owner, Bestia origin) {
		this(owner, origin, BaseValues.getNewIndividualValues());

		// no op.
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
		final Point defaultLocation = new Point(0, 0);
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
		if (name == null || name.isEmpty()) {
			// In some cases, if the PlayerBestia is instanced via default ctor
			// this might even be null. Avoid it.
			if (originBestia == null) {
				return "";
			}
			return originBestia.getDefaultName();
		}

		return name;
	}

	public void setName(String name) {
		this.name = Objects.requireNonNull(name);
	}

	public Point getSavePosition() {
		return savePosition;
	}

	public void setSavePosition(Point savePosition) {

		this.savePosition = Objects.requireNonNull(savePosition);
	}

	public Point getCurrentPosition() {
		return currentPosition;
	}

	public void setCurrentPosition(Point currentPosition) {

		this.currentPosition = Objects.requireNonNull(currentPosition);
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

	public long getId() {
		return id;
	}

	/**
	 * Returns the entity ID of this player bestia if the bestia was spawned. If
	 * no entity was spawned the ID is 0.
	 * 
	 * @return The entity ID of the player bestia. 0 if no entity was spawned.
	 */
	public long getEntityId() {
		return entityId;
	}

	public void setEntityId(long entityId) {
		this.entityId = entityId;
	}

	public StatusValues getStatusValues() {
		return statusValues;
	}

	public Account getMaster() {
		return master;
	}

	public void setMaster(Account master) {
		this.master = master;
	}

	@Override
	public int hashCode() {
		return (int) (getId() * 21);
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

	/**
	 * Owner can be set. It is as intended to allow setting of null as the
	 * owner. Because of DB restrictions persisting such an entity is not
	 * possible but setting the owner to null can be important to save memory
	 * when transferring the player bestia as entity to the cache.
	 * 
	 * @param owner
	 *            The new owner of this bestia.
	 */
	public void setOwner(Account owner) {
		this.owner = owner;
	}

	public void setStatusValues(StatusValues sv) {
		this.statusValues = Objects.requireNonNull(sv);
	}
}
