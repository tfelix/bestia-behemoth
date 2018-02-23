package net.bestia.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bestias")
@PrimaryKeyJoinColumn(name = "bestia_id")
public class Bestia implements Serializable {

	@Transient
	private static final long serialVersionUID = 1L;

	@Id
	@JsonIgnore
	private int id;

	@Column(name = "bestia_db_name", unique = true, nullable = false, length = 100)
	@JsonProperty("bdbn")
	private String databaseName;

	@Column(name = "default_name", nullable = false, length = 100)
	@JsonIgnore
	private String defaultName;

	@Enumerated(EnumType.STRING)
	@JsonProperty("ele")
	private Element element;

	@JsonProperty("img")
	private String image = "";

	@JsonProperty("sp")
	@AttributeOverrides({ @AttributeOverride(name = "type", column = @Column(name = "visualType")) })
	private SpriteInfo spriteInfo;

	@JsonIgnore
	private int expGained;

	@Enumerated(EnumType.STRING)
	@JsonProperty("t")
	private BestiaType type;

	@JsonIgnore
	private int level;

	@JsonIgnore
	private boolean isBoss;

	@JsonIgnore
	@OneToMany(cascade = CascadeType.ALL, mappedBy = "bestia", fetch = FetchType.EAGER)
	private List<DropItem> dropItems = new ArrayList<>();

	/*-
	 * This values is added to each player bestia when it kill another bestia from this kind until the maximum amount
	 * of effort value is reached. The distribution of the effort values is calculated by the distribution of the real
	 * status values of a bestia and depending on its level.
	 * 
	 * Level  1 - 25: Totel EVs:  1
	 * Level 26 - 50: Totel EVs:  2
	 * Level 51 - 75: Totel EVs:  3
	 * Level 76 - 100: Totel EVs: 4
	 * 
	 */
	@Embedded
	@JsonIgnore
	@Transient
	@AttributeOverrides({ @AttributeOverride(name = "hp", column = @Column(name = "evHp")),
			@AttributeOverride(name = "mana", column = @Column(name = "evMana")),
			@AttributeOverride(name = "strength", column = @Column(name = "evStr")),
			@AttributeOverride(name = "vitality", column = @Column(name = "evVit")),
			@AttributeOverride(name = "intelligence", column = @Column(name = "evInt")),
			@AttributeOverride(name = "willpower", column = @Column(name = "evWill")),
			@AttributeOverride(name = "agility", column = @Column(name = "evAgi")),
			@AttributeOverride(name = "dexterity", column = @Column(name = "evDex"))})
	private BaseValues effortValues;

	@JsonIgnore
	@Transient
	private StatusPoints statusPoints;

	/**
	 * Override the names because the are the same like in status points. Both
	 * entities are embedded so we need individual column names. This values is
	 * added to each bestia when it kill another bestia from this kind.
	 */
	@Embedded
	@JsonIgnore
	private BaseValues baseValues;

	/**
	 * Script which will be attached to this bestia.
	 */
	@JsonIgnore
	private String scriptExec;

	public Bestia() {
		// no op.
	}
	
	public Bestia(String databaseName) {
		
		this.databaseName = databaseName;
	}

	/**
	 * Calculates the effort values depending on its level and the base values.
	 */
	private void calculateEffortValues() {
		effortValues = new BaseValues();

		int maxEffortVal;
		if (level <= 25) {
			maxEffortVal = 1;
		} else if (level <= 50) {
			maxEffortVal = 2;
		} else if (level <= 75) {
			maxEffortVal = 3;
		} else {
			maxEffortVal = 4;
		}

		// Calculate total amount of base values and distribute accordingly.
		final float baseMax = baseValues.getAttack() + baseValues.getVitality() + baseValues.getHp() + baseValues.getMana()
				+ baseValues.getIntelligence() + baseValues.getAgility() + baseValues.getWillpower();

		final int evAtk = Math.round(maxEffortVal * (baseValues.getAttack() / baseMax));
		final int evDef = Math.round(maxEffortVal * (baseValues.getVitality() / baseMax));
		final int evHp = Math.round(maxEffortVal * (baseValues.getHp() / baseMax));
		final int evMana = Math.round(maxEffortVal * (baseValues.getMana() / baseMax));

		final int evSpAtk = Math.round(maxEffortVal * (baseValues.getIntelligence() / baseMax));
		final int evSpDef = Math.round(maxEffortVal * (baseValues.getWillpower() / baseMax));
		final int evSpd = Math.round(maxEffortVal * (baseValues.getAgility() / baseMax));

		// Set the values.
		effortValues.setAttack(evAtk);
		effortValues.setVitality(evDef);
		effortValues.setHp(evHp);
		effortValues.setMana(evMana);
		effortValues.setIntelligence(evSpAtk);
		effortValues.setWillpower(evSpDef);
		effortValues.setAgility(evSpd);
	}

	/**
	 * Returns the type of the bestia.
	 * 
	 * @return The type of the bestia.
	 */
	public BestiaType getType() {
		return type;
	}

	public BaseValues getBaseValues() {
		return baseValues;
	}

	/**
	 * The database name.
	 * 
	 * @return The database name.
	 */
	public String getDatabaseName() {
		return databaseName;
	}

	public String getImage() {
		return image;
	}

	public SpriteInfo getSpriteInfo() {
		return spriteInfo;
	}

	/**
	 * Experience points gained if bestia was defeated.
	 * 
	 * @return
	 */
	public int getExpGained() {
		return expGained;
	}

	public int getLevel() {
		return level;
	}

	public int getId() {
		return id;
	}

	public boolean isBoss() {
		return isBoss;
	}

	public String getDefaultName() {
		return defaultName;
	}

	public void setDefaultName(String defaultName) {
		this.defaultName = defaultName;
	}

	public String getScriptExec() {
		return scriptExec;
	}

	/**
	 * Returns a list with the items being dropped by this bestia upon its
	 * death.
	 * 
	 * @return
	 */
	public List<DropItem> getDropItems() {
		return dropItems;
	}

	/**
	 * Returns the status points of this bestia.
	 * 
	 * @return The status points.
	 */
	public StatusPoints getStatusPoints() {
		return statusPoints;
	}

	/**
	 * Returns the NPC bestias effort values which will be granted if the bestia
	 * was killed by a player.
	 * 
	 * @return The earned effort values if a bestia was killed by a player.
	 */
	public BaseValues getEffortValues() {
		if (effortValues == null) {
			calculateEffortValues();
		}
		return effortValues;
	}

	@Override
	public String toString() {
		return String.format("Bestia[dbName: %s, id: %d, level: %d]", databaseName, id, level);
	}

	public Element getElement() {
		return element;
	}

}
