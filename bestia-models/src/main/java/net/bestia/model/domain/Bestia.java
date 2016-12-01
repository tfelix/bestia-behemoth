package net.bestia.model.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.model.entity.VisualType;

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
	
	@Enumerated(EnumType.STRING)
	@JsonIgnore
	private VisualType visual;

	@JsonIgnore
	private String sprite = "";

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
	@AttributeOverrides({ @AttributeOverride(name = "hp", column = @Column(name = "evHp")),
			@AttributeOverride(name = "mana", column = @Column(name = "evMana")),
			@AttributeOverride(name = "atk", column = @Column(name = "evAtk")),
			@AttributeOverride(name = "def", column = @Column(name = "evDef")),
			@AttributeOverride(name = "spAtk", column = @Column(name = "evSpAtk")),
			@AttributeOverride(name = "spDef", column = @Column(name = "evSpDef")),
			@AttributeOverride(name = "spd", column = @Column(name = "evSpd")) })
	private BaseValues effortValues;

	@JsonIgnore
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
		final float baseMax = baseValues.getAtk() + baseValues.getDef() + baseValues.getHp() + baseValues.getMana()
				+ baseValues.getSpAtk() + baseValues.getSpd() + baseValues.getSpDef();

		final int evAtk = Math.round(maxEffortVal * (baseValues.getAtk() / baseMax));
		final int evDef = Math.round(maxEffortVal * (baseValues.getDef() / baseMax));
		final int evHp = Math.round(maxEffortVal * (baseValues.getHp() / baseMax));
		final int evMana = Math.round(maxEffortVal * (baseValues.getMana() / baseMax));

		final int evSpAtk = Math.round(maxEffortVal * (baseValues.getSpAtk() / baseMax));
		final int evSpDef = Math.round(maxEffortVal * (baseValues.getSpDef() / baseMax));
		final int evSpd = Math.round(maxEffortVal * (baseValues.getSpd() / baseMax));

		// Set the values.
		effortValues.setAtk(evAtk);
		effortValues.setDef(evDef);
		effortValues.setHp(evHp);
		effortValues.setMana(evMana);
		effortValues.setSpAtk(evSpAtk);
		effortValues.setSpDef(evSpDef);
		effortValues.setSpd(evSpd);
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

	public String getSprite() {
		return sprite;
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
	
	public VisualType getVisual() {
		return visual;
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

}
