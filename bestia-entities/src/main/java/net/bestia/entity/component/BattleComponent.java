package net.bestia.entity.component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Contains data regarding a battle. This is important to distribute EXP for
 * example after a battle has taken place. It also keeps track which entity is
 * currently being attacked by this entity.
 * 
 * @author Thomas Felix
 *
 */
public class BattleComponent extends Component {

	private static final long serialVersionUID = 1L;

	private static final class DamageEntry {
		public long time;
		public long damage;

		public DamageEntry(long time, long damage) {

			this.time = time;
			this.damage = damage;
		}
	}

	/**
	 * Number of entities whose damage is tracked against the entity.
	 */
	private static final int MAX_ENTITY_DAMAGE_TRACK = 20;

	/**
	 * Time delay until a received damage is beeing removed.
	 */
	private static final int DAMAGE_ENTRY_REMOVE_DELAY_MS = 30 * 60 * 1000;

	private final Map<Long, DamageEntry> damageReceived = new HashMap<>();

	public BattleComponent(long id) {
		super(id);
		// no op
	}

	/**
	 * Removes all damage entries which are older then
	 * {@link #DAMAGE_ENTRY_REMOVE_DELAY_MS}.
	 */
	public void pruneDamageEntries() {
		final long curTime = System.currentTimeMillis();
		damageReceived.entrySet().removeIf(x -> x.getValue().time + DAMAGE_ENTRY_REMOVE_DELAY_MS < curTime);
	}

	public void addDamageReceived(long originEntityId, int damage) {

		final long curTime = System.currentTimeMillis();

		if (damageReceived.containsKey(originEntityId)) {
			damageReceived.computeIfPresent(originEntityId, (k, v) -> {
				v.damage += damage;
				return v;
			});
		} else {
			// Add the damage and make sure not more then
			// MAX_ENTITY_DAMAGE_TRACK is present.
			damageReceived.put(originEntityId, new DamageEntry(curTime, damage));

			if (damageReceived.size() > MAX_ENTITY_DAMAGE_TRACK) {
				final long minKeptDamage = damageReceived.values()
						.stream()
						.mapToLong(x -> x.damage)
						.sorted()
						.skip(MAX_ENTITY_DAMAGE_TRACK)
						.findFirst()
						.orElse(0);

				damageReceived.entrySet().removeIf(x -> x.getValue().damage < minKeptDamage);
			}
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(damageReceived);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof BattleComponent)) {
			return false;
		}
		final BattleComponent other = (BattleComponent) obj;
		return Objects.equals(damageReceived, other.damageReceived);
	}
}
