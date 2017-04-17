package net.bestia.zoneserver.entity;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import net.bestia.messages.chat.ChatMessage;
import net.bestia.messages.entity.EntityDamageMessage;
import net.bestia.messages.entity.EntityMoveInternalMessage;
import net.bestia.model.battle.Damage;
import net.bestia.model.battle.StatusBasedValueModifier;
import net.bestia.model.battle.StatusBasedValuesDecorator;
import net.bestia.model.battle.StatusPointsDecorator;
import net.bestia.model.battle.StatusPointsModifier;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.EquipmentSlot;
import net.bestia.model.domain.Item;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.StatusPointsImpl;
import net.bestia.model.entity.InteractionType;
import net.bestia.model.entity.StatusBasedValuesImpl;
import net.bestia.model.geometry.CollisionShape;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.script.StatusEffectScript;

@Service
public class BestiaService {
	
	/**
	 * Recalculates the status values of a bestia. It uses the EVs, IVs and
	 * BaseValues. Must be called after the level of a bestia has changed.
	 */
	protected void calculateStatusPoints() {

		baseStatusPoints = new StatusPointsImpl();

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
		public float getMovementSpeed() {
			return statusBasedValues.getWalkspeed() / 100f;
		}

		@Override
		public void setLevel(int level) {
			super.setLevel(level);

			statusBasedValues.setLevel(level);
		}

		/**
		 * Moves the entity a certain path. This will trigger a message to the actor
		 * system.
		 */
		@Override
		public void moveTo(List<Point> path) {
			Objects.requireNonNull(path);

			final EntityMoveInternalMessage moveMsg = new EntityMoveInternalMessage(getId(), path);
			getContext().sendMessage(moveMsg);
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


		public Set<Long> getLastSeenEntities() {
			return lastSeenEntities;
		}

		public long getAccountId() {
			return accountId;
		}

		@Override
		public boolean isVisible() {
			return true;
		}

		@Override
		public Set<InteractionType> getPossibleInteractions(Interactable interacter) {
			return Collections.emptySet();
		}

		@Override
		public Set<InteractionType> getInteractions() {
			return Collections.emptySet();
		}

		@Override
		public void triggerInteraction(InteractionType type, Interactable interactor) {
			// TODO Auto-generated method stub

		}

		@Override
		public float getMaxWeight() {
			// TODO Auto-generated method stub
			return 100;
		}

		@Override
		public float getWeight() {
			// TODO Auto-generated method stub
			return 10;
		}

		@Override
		public int getMaxItemCount() {
			return 100;
		}

		@Override
		public int getItemCount() {	
			return 0;
		}

		@Override
		public boolean addItem(Item item, int amount) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean removeItem(Item item, int amount) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean dropItem(Item item, int amount) {
			// TODO Auto-generated method stub
			return false;
		}

		/**
		 * Returns the ID of the wrapped player bestia.
		 * 
		 * @return The id of the player bestia.
		 */
		public int getPlayerBestiaId() {
			return playerBestia.getId();
		}

		@Override
		public int getLevel() {
			return playerBestia.getLevel();
		}

		/**
		 * Restores the owner of the wrapped player bestia object. When setup in
		 * order to save memory the owner reference is removed from the player
		 * bestia. In order to get the modified player bestia it will need its owner
		 * and then return the restored object.
		 * 
		 * @param owner
		 * @return
		 */
		public PlayerBestia restorePlayerBestia(Account owner) {
			Objects.requireNonNull(owner);
			if (owner.getId() != getAccountId()) {
				throw new IllegalArgumentException("Wrong PlayerBestia object given for update.");
			}

			// Perform the update process.
			playerBestia.setOwner(owner);
			return playerBestia;
		}

		@Override
		public Point getPosition() {
			return playerBestia.getCurrentPosition();
		}

		@Override
		public void setPosition(long x, long y) {
			playerBestia.setCurrentPosition(new Point(x, y));
			// Update all the sorrounding entities.
			super.setPosition(x, y);
		}

		@Override
		public CollisionShape getShape() {
			return getPosition();
		}

		@Override
		public void setShape(CollisionShape shape) {
			// TODO NOOP. Currently only a point shape is used.
		}

		@Override
		public boolean isDead() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void addExp(int exp) {
			playerBestia.setExp(playerBestia.getExp() + exp);
			checkLevelup();
		}

		@Override
		public int getKilledExp() {
			// TODO Das hier noch austauschen.
			return 10;
		}
	}

}
