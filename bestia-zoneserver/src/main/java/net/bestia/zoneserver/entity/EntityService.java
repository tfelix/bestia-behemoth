package net.bestia.zoneserver.entity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IdGenerator;
import com.hazelcast.query.EntryObject;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.PredicateBuilder;

import net.bestia.messages.chat.ChatMessage;
import net.bestia.messages.entity.EntityDamageMessage;
import net.bestia.model.battle.Damage;
import net.bestia.model.battle.StatusBasedValueModifier;
import net.bestia.model.battle.StatusBasedValuesDecorator;
import net.bestia.model.battle.StatusPointsDecorator;
import net.bestia.model.battle.StatusPointsModifier;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusPointsImpl;
import net.bestia.model.entity.StatusBasedValuesImpl;
import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.entity.components.Component;
import net.bestia.zoneserver.entity.components.PositionComponent;
import net.bestia.zoneserver.entity.components.StatusComponent;
import net.bestia.zoneserver.script.StatusEffectScript;

@Service
public class EntityService {

	private final static String ECS_ENTITY_MAP = "entities.ecs";
	private final static String ENTITIES_ID_GEN = "entities.id";

	private final IMap<Long, Entity> entities;
	private final IdGenerator idCounter;
	private final ComponentService componentService;

	@Autowired
	public EntityService(HazelcastInstance hz, ComponentService compService) {

		Objects.requireNonNull(hz);

		idCounter = hz.getIdGenerator(ENTITIES_ID_GEN);
		entities = hz.getMap(ECS_ENTITY_MAP);

		this.componentService = Objects.requireNonNull(compService);
	}

	/**
	 * Returns a new ID either from the internal pool of the id generator of
	 * hazelcast.
	 * 
	 * @return A new, currently unused id.
	 */
	private long getNewEntityId() {
		return idCounter.newId();
	}

	/**
	 * Returns a fresh entity which can be used inside the system. It already
	 * has a unique ID and can be used to persist date.
	 * 
	 * @return
	 */
	public Entity newEntity() {
		return new Entity(getNewEntityId());
	}

	/**
	 * Deletes the entity
	 * 
	 * @param entityId
	 *            The entity id to remove from the memory database.
	 */
	public void delete(Entity entity) {
		Objects.requireNonNull(entity);

		entities.lock(entity.getId());
		try {
			// Delete all components.
			componentService.removeAllComponents(entity);
			entities.delete(entity.getId());
		} finally {
			entities.unlock(entity.getId());
		}
	}

	/**
	 * Deletes the entity given by its id.
	 * 
	 * @param entity
	 *            Removes the entity.
	 */
	public void delete(long entityId) {
		delete(entities.get(entityId));
	}

	/**
	 * Puts the entity into the memory database for access for the bestia
	 * system. Before saving (and thus serializing it). The context must be
	 * removed.
	 * 
	 * @param entity
	 *            The entity to put into the memory database.
	 */
	public void save(Entity entity) {
		// Remove entity context since it can not be serialized.
		entities.lock(entity.getId());
		try {
			entities.put(entity.getId(), entity);
		} finally {
			entities.unlock(entity.getId());
		}
	}

	/**
	 * Returns the ID entity with the given ID.
	 * 
	 * @param entityId
	 * @return The {@link Entity} or NULL if no such id is stored.
	 */
	public Entity getEntity(long entityId) {
		entities.lock(entityId);
		try {
			final Entity e = entities.get(entityId);
			return e;
		} finally {
			entities.unlock(entityId);
		}
	}

	public Map<Long, Entity> getAllEntities(Set<Long> ids) {
		return entities.getAll(ids);
	}

	/**
	 * Returns all the entities which are in range.
	 * 
	 * @param area
	 * @return
	 */
	public Set<Entity> getEntitiesInRange(Rect area) {

		// TODO Das muss noch effektiver gestaltet werden.
		EntryObject e = new PredicateBuilder().getEntryObject();
		@SuppressWarnings("rawtypes")
		Predicate posPred = e.get("components").in(PositionComponent.class.getName());
		return entities.values(posPred).stream().filter(entity -> {
			Optional<PositionComponent> comp = componentService.getComponent(entity.getId(), PositionComponent.class);
			if (!comp.isPresent()) {
				return false;
			}
			return comp.get().getShape().collide(area);
		}).collect(Collectors.toSet());
	}

	/**
	 * Returns all the entities which are in range and have a certain component.
	 * Note that they ALWAYS must have a position component in order to get
	 * localized.
	 * 
	 * @param area
	 * @return
	 */
	public Set<Entity> getEntitiesInRange(Rect area, Class<? extends Component> clazz) {
		final Set<Class<? extends Component>> comps = new HashSet<>(Arrays.asList(clazz));
		return getEntitiesInRange(area).stream().filter(x -> comps.contains(x.getClass())).collect(Collectors.toSet());
	}

	/**
	 * Recalculates the status values of entity if it has a {@link StatusComponent} attached. It uses the EVs, IVs and
	 * BaseValues. Must be called after the level of a bestia has changed.
	 */
	public void calculateStatusPoints(Entity e) {

		StatusPoints baseStatusPoints = new StatusPointsImpl();

		final int atk = (baseValues.getAttack() * 2 + ivs.getAttack()
				+ effortValues.getAttack() / 4) * getLevel() / 100 + 5;

		final int def = (baseValues.getVitality() * 2 + ivs.getVitality()
				+ effortValues.getVitality() / 4) * getLevel() / 100 + 5;

		final int spatk = (baseValues.getIntelligence() * 2 + ivs.getIntelligence()
				+ effortValues.getIntelligence() / 4) * getLevel() / 100 + 5;

		final int spdef = (baseValues.getWillpower() * 2 + ivs.getWillpower()
				+ effortValues.getWillpower() / 4) * getLevel() / 100 + 5;

		int spd = (baseValues.getAgility() * 2 + ivs.getAgility()
				+ effortValues.getAgility() / 4) * getLevel() / 100 + 5;

		final int maxHp = baseValues.getHp() * 2 + ivs.getHp()
				+ effortValues.getHp() / 4 * getLevel() / 100 + 10 + getLevel();

		final int maxMana = baseValues.getMana() * 2 + ivs.getMana()
				+ effortValues.getMana() / 4 * getLevel() / 100 + 10 + getLevel() * 2;

		baseStatusPoints.setMaxHp(maxHp);
		baseStatusPoints.setMaxMana(maxMana);
		baseStatusPoints.setStrenght(atk);
		baseStatusPoints.setVitality(def);
		baseStatusPoints.setIntelligence(spatk);
		baseStatusPoints.setMagicDefense(spdef);
		baseStatusPoints.setAgility(spd);

		baseStatusPointModified = new StatusPointsDecorator(baseStatusPoints);
		baseStatusPointModified.clearModifier();

		// Get all the attached script mods.
		for (StatusEffectScript statScript : statusEffectsScripts) {
			final StatusPointsModifier mod = statScript.onStatusPoints(baseStatusPoints);
			baseStatusPointModified.addModifier(mod);
		}

		statusBasedValues = new StatusBasedValuesImpl(baseStatusPointModified, getLevel());
		statusBasedValuesModified = new StatusBasedValuesDecorator(statusBasedValues);
		statusBasedValuesModified.clearModifier();

		// Get all the attached script mods.
		for (StatusEffectScript statScript : statusEffectsScripts) {
			final StatusBasedValueModifier mod = statScript.onStatusBasedValues(statusBasedValues);
			statusBasedValuesModified.addStatusModifier(mod);
		}

	@Override
	public Damage takeDamage(Damage damage) {
		// TODO Den Schaden richtig verrechnen.
		int curHp = getStatusPoints().getCurrentHp();

		// Send the message to all clients in visible range.
		getContext().sendMessage(new EntityDamageMessage(getId(), damage));

		if (curHp - damage.getDamage() > 0) {
			getStatusPoints().setCurrentHp(curHp - damage.getDamage());

		} else {
			kill();
		}

		return damage;
	}

	@Override
	public void setLevel(int level) {
		super.setLevel(level);

		statusBasedValues.setLevel(level);
	}

	private void checkLevelup() {

		final int neededExp = (int) Math.round(Math.pow(getLevel(), 3) / 10 + 15 + getLevel() * 1.5);

		if (playerBestia.getExp() > neededExp) {
			playerBestia.setExp(playerBestia.getExp() - neededExp);
			playerBestia.setLevel(playerBestia.getLevel() + 1);
			getContext().sendMessage(
					ChatMessage.getSystemMessage(getAccountId(), "T: Bestia advanced to level " + getLevel()));
			setLevel(playerBestia.getLevel());
			calculateStatusPoints();
			checkLevelup();
		}
	}

	public void addExp(int exp) {
		playerBestia.setExp(playerBestia.getExp() + exp);
		checkLevelup();
	}
}
