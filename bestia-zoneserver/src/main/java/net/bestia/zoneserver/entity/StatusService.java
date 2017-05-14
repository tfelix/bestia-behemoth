package net.bestia.zoneserver.entity;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.BaseValues;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusPointsImpl;
import net.bestia.model.entity.StatusBasedValues;
import net.bestia.zoneserver.entity.components.PlayerComponent;
import net.bestia.zoneserver.entity.components.StatusComponent;

/**
 * The service class is responsible for recalculating the status values for a
 * entity. All kind of calculations are considered, this means equipment, status
 * effects and so on.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class StatusService {

	private static final Logger LOG = LoggerFactory.getLogger(StatusService.class);

	private final EntityService entityService;

	private final PlayerBestiaDAO playerBestiaDao;

	@Autowired
	public StatusService(EntityService entityService, PlayerBestiaDAO playerBestiaDao) {

		this.entityService = Objects.requireNonNull(entityService);
		this.playerBestiaDao = Objects.requireNonNull(playerBestiaDao);
	}

	/**
	 * This method should be used to retrive status based values for an entity.
	 * In case this values can not be retrieved thought the component is
	 * available the values will be recalculated.
	 * 
	 * @param entity
	 * @return
	 */
	public StatusBasedValues getStatusBasedValues(Entity entity) {
		return null;
	}

	/**
	 * Returns the status points of an entity which has this component. If the
	 * values are not set then they are recalculated.
	 * 
	 * @param entity
	 * @return
	 */
	public StatusPoints getStatusPoints(Entity entity) {

		final StatusComponent statusComp = entityService.getComponent(entity, StatusComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		if (statusComp.getStatusPoints() == null) {
			calculateStatusPoints(entity, statusComp);
		}

		return statusComp.getStatusPoints();
	}

	/**
	 * Recalculates the status values of entity if it has a
	 * {@link StatusComponent} attached. It uses the EVs, IVs and BaseValues.
	 * Must be called after the level of a bestia has changed. Currently this
	 * method only accepts entity with player and status components. This will
	 * change in the future.
	 */
	private void calculateStatusPoints(Entity entity, StatusComponent statusComp) {
		Objects.requireNonNull(entity);
		
		LOG.trace("Calculate status points for entity {}.", entity);

		final PlayerComponent playerComp = entityService.getComponent(entity, PlayerComponent.class)
				.orElseThrow(IllegalStateException::new);

		StatusPoints baseStatusPoints = new StatusPointsImpl();

		PlayerBestia pb = playerBestiaDao.findOne(playerComp.getPlayerBestiaId());

		final BaseValues baseValues = pb.getBaseValues();
		final BaseValues effortValues = pb.getEffortValues();
		final BaseValues ivs = pb.getIndividualValue();

		final int atk = (baseValues.getAttack() * 2 + ivs.getAttack()
				+ effortValues.getAttack() / 4) * statusComp.getLevel() / 100 + 5;

		final int def = (baseValues.getVitality() * 2 + ivs.getVitality()
				+ effortValues.getVitality() / 4) * statusComp.getLevel() / 100 + 5;

		final int spatk = (baseValues.getIntelligence() * 2 + ivs.getIntelligence()
				+ effortValues.getIntelligence() / 4) * statusComp.getLevel() / 100 + 5;

		final int spdef = (baseValues.getWillpower() * 2 + ivs.getWillpower()
				+ effortValues.getWillpower() / 4) * statusComp.getLevel() / 100 + 5;

		int spd = (baseValues.getAgility() * 2 + ivs.getAgility()
				+ effortValues.getAgility() / 4) * statusComp.getLevel() / 100 + 5;

		final int maxHp = baseValues.getHp() * 2 + ivs.getHp()
				+ effortValues.getHp() / 4 * statusComp.getLevel() / 100 + 10 + statusComp.getLevel();

		final int maxMana = baseValues.getMana() * 2 + ivs.getMana()
				+ effortValues.getMana() / 4 * statusComp.getLevel() / 100 + 10 + statusComp.getLevel() * 2;

		baseStatusPoints.setMaxHp(maxHp);
		baseStatusPoints.setMaxMana(maxMana);
		baseStatusPoints.setStrenght(atk);
		baseStatusPoints.setVitality(def);
		baseStatusPoints.setIntelligence(spatk);
		baseStatusPoints.setMagicDefense(spdef);
		baseStatusPoints.setAgility(spd);
		
		statusComp.setStatusPoints(baseStatusPoints);
		
		entityService.updateComponent(statusComp);
	}

	/*
	 * StatusPointsDecorator baseStatusPointModified = new
	 * StatusPointsDecorator(baseStatusPoints);
	 * 
	 * // Get all the attached script mods. for (StatusEffectScript statScript :
	 * statusEffectsScripts) { final StatusPointsModifier mod =
	 * statScript.onStatusPoints(baseStatusPoints);
	 * baseStatusPointModified.addModifier(mod); }
	 * 
	 * statusBasedValues=new
	 * StatusBasedValuesImpl(baseStatusPointModified,getLevel());
	 * statusBasedValuesModified=new
	 * StatusBasedValuesDecorator(statusBasedValues);statusBasedValuesModified.
	 * clearModifier();
	 * 
	 * // Get all the attached script mods. for (StatusEffectScript statScript :
	 * statusEffectsScripts) { final StatusBasedValueModifier mod =
	 * statScript.onStatusBasedValues(statusBasedValues);
	 * statusBasedValuesModified.addStatusModifier(mod); }
	 */

	public void setLevel(Entity entity, int level) {
		final StatusComponent statusComp = entityService.getComponent(entity, StatusComponent.class)
				.orElseThrow(IllegalStateException::new);

		statusComp.setLevel(level);
		calculateStatusPoints(entity, statusComp);
	}

	private void checkLevelup(Entity entity, StatusComponent statusComp) {

		final int neededExp = (int) Math
				.round(Math.pow(statusComp.getLevel(), 3) / 10 + 15 + statusComp.getLevel() * 1.5);

		if (statusComp.getExp() > neededExp) {
			statusComp.setExp(statusComp.getExp() - neededExp);
			statusComp.setLevel(statusComp.getLevel() + 1);
			checkLevelup(entity, statusComp);
		} else {
			calculateStatusPoints(entity, statusComp);
			entityService.updateComponent(statusComp);
		}
	}

	public void addExp(Entity entity, int exp) {
		final StatusComponent statusComp = entityService.getComponent(entity, StatusComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		statusComp.setExp(statusComp.getExp() + exp);
		checkLevelup(entity, statusComp);
	}

}
