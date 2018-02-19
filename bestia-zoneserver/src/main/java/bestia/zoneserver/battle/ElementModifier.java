package bestia.zoneserver.battle;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import bestia.model.domain.Element;

/**
 * Returns the attack damage modifier for a given elemental set of the attacker
 * and defender. Since this class is immutable it is thread-safe.
 * 
 * @author Thomas Felix
 *
 */
final class ElementModifier {

	/**
	 * Wraps the creation of element keys to retrieve the element modifier.
	 * 
	 * @author Thomas Felix
	 *
	 */
	private static class ElementKey {
		private final Element el1;
		private final Element el2;

		ElementKey(Element el1, Element el2) {
			this.el1 = el1;
			this.el2 = el2;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof ElementKey))
				return false;
			ElementKey key = (ElementKey) o;
			return el1 == key.el1 && el2 == key.el2;
		}

		@Override
		public int hashCode() {
			return 31 * el1.hashCode() + el2.hashCode();
		}
	}

	/**
	 * The damage table is saved as integer. Basically its a fixed point. 100
	 * means a multiplier factor of 1.00.
	 */
	private final static Map<ElementKey, Integer> elementMap = new HashMap<>();
	private final static Set<Element> legalAttackElements = EnumSet.noneOf(Element.class);
	static {
		// Setup the elements.
		// LEVEL 1
		elementMap.put(new ElementKey(Element.NORMAL, Element.NORMAL), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.WATER), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.EARTH), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.FIRE), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.WIND), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.POISON), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.HOLY), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.SHADOW), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.GHOST), 25);
		elementMap.put(new ElementKey(Element.NORMAL, Element.UNDEAD), 100);

		elementMap.put(new ElementKey(Element.WATER, Element.NORMAL), 100);
		elementMap.put(new ElementKey(Element.WATER, Element.WATER), 25);
		elementMap.put(new ElementKey(Element.WATER, Element.EARTH), 100);
		elementMap.put(new ElementKey(Element.WATER, Element.FIRE), 150);
		elementMap.put(new ElementKey(Element.WATER, Element.WIND), 50);
		elementMap.put(new ElementKey(Element.WATER, Element.POISON), 100);
		elementMap.put(new ElementKey(Element.WATER, Element.HOLY), 75);
		elementMap.put(new ElementKey(Element.WATER, Element.SHADOW), 100);
		elementMap.put(new ElementKey(Element.WATER, Element.GHOST), 100);
		elementMap.put(new ElementKey(Element.WATER, Element.UNDEAD), 100);

		elementMap.put(new ElementKey(Element.EARTH, Element.NORMAL), 100);
		elementMap.put(new ElementKey(Element.EARTH, Element.WATER), 100);
		elementMap.put(new ElementKey(Element.EARTH, Element.EARTH), 100);
		elementMap.put(new ElementKey(Element.EARTH, Element.FIRE), 50);
		elementMap.put(new ElementKey(Element.EARTH, Element.WIND), 150);
		elementMap.put(new ElementKey(Element.EARTH, Element.POISON), 100);
		elementMap.put(new ElementKey(Element.EARTH, Element.HOLY), 75);
		elementMap.put(new ElementKey(Element.EARTH, Element.SHADOW), 100);
		elementMap.put(new ElementKey(Element.EARTH, Element.GHOST), 100);
		elementMap.put(new ElementKey(Element.EARTH, Element.UNDEAD), 100);

		elementMap.put(new ElementKey(Element.FIRE, Element.NORMAL), 100);
		elementMap.put(new ElementKey(Element.FIRE, Element.WATER), 50);
		elementMap.put(new ElementKey(Element.FIRE, Element.EARTH), 150);
		elementMap.put(new ElementKey(Element.FIRE, Element.FIRE), 25);
		elementMap.put(new ElementKey(Element.FIRE, Element.WIND), 100);
		elementMap.put(new ElementKey(Element.FIRE, Element.POISON), 100);
		elementMap.put(new ElementKey(Element.FIRE, Element.HOLY), 75);
		elementMap.put(new ElementKey(Element.FIRE, Element.SHADOW), 100);
		elementMap.put(new ElementKey(Element.FIRE, Element.GHOST), 100);
		elementMap.put(new ElementKey(Element.FIRE, Element.UNDEAD), 125);

		elementMap.put(new ElementKey(Element.WIND, Element.NORMAL), 100);
		elementMap.put(new ElementKey(Element.WIND, Element.WATER), 175);
		elementMap.put(new ElementKey(Element.WIND, Element.EARTH), 50);
		elementMap.put(new ElementKey(Element.WIND, Element.FIRE), 100);
		elementMap.put(new ElementKey(Element.WIND, Element.WIND), 25);
		elementMap.put(new ElementKey(Element.WIND, Element.POISON), 100);
		elementMap.put(new ElementKey(Element.WIND, Element.HOLY), 75);
		elementMap.put(new ElementKey(Element.WIND, Element.SHADOW), 100);
		elementMap.put(new ElementKey(Element.WIND, Element.GHOST), 100);
		elementMap.put(new ElementKey(Element.WIND, Element.UNDEAD), 100);

		elementMap.put(new ElementKey(Element.POISON, Element.NORMAL), 100);
		elementMap.put(new ElementKey(Element.POISON, Element.WATER), 100);
		elementMap.put(new ElementKey(Element.POISON, Element.EARTH), 125);
		elementMap.put(new ElementKey(Element.POISON, Element.FIRE), 125);
		elementMap.put(new ElementKey(Element.POISON, Element.WIND), 125);
		elementMap.put(new ElementKey(Element.POISON, Element.POISON), 0);
		elementMap.put(new ElementKey(Element.POISON, Element.HOLY), 75);
		elementMap.put(new ElementKey(Element.POISON, Element.SHADOW), 50);
		elementMap.put(new ElementKey(Element.POISON, Element.GHOST), 100);
		elementMap.put(new ElementKey(Element.POISON, Element.UNDEAD), -25);

		elementMap.put(new ElementKey(Element.HOLY, Element.HOLY), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.WATER), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.EARTH), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.FIRE), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.WIND), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.POISON), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.HOLY), 0);
		elementMap.put(new ElementKey(Element.HOLY, Element.SHADOW), 125);
		elementMap.put(new ElementKey(Element.HOLY, Element.GHOST), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.UNDEAD), 150);

		elementMap.put(new ElementKey(Element.SHADOW, Element.NORMAL), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.WATER), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.EARTH), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.FIRE), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.WIND), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.POISON), 50);
		elementMap.put(new ElementKey(Element.SHADOW, Element.HOLY), 125);
		elementMap.put(new ElementKey(Element.SHADOW, Element.SHADOW), 0);
		elementMap.put(new ElementKey(Element.SHADOW, Element.GHOST), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.UNDEAD), -25);

		elementMap.put(new ElementKey(Element.GHOST, Element.NORMAL), 25);
		elementMap.put(new ElementKey(Element.GHOST, Element.WATER), 100);
		elementMap.put(new ElementKey(Element.GHOST, Element.EARTH), 100);
		elementMap.put(new ElementKey(Element.GHOST, Element.FIRE), 100);
		elementMap.put(new ElementKey(Element.GHOST, Element.WIND), 100);
		elementMap.put(new ElementKey(Element.GHOST, Element.POISON), 100);
		elementMap.put(new ElementKey(Element.GHOST, Element.HOLY), 75);
		elementMap.put(new ElementKey(Element.GHOST, Element.SHADOW), 75);
		elementMap.put(new ElementKey(Element.GHOST, Element.GHOST), 125);
		elementMap.put(new ElementKey(Element.GHOST, Element.UNDEAD), 100);

		elementMap.put(new ElementKey(Element.UNDEAD, Element.NORMAL), 100);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.WATER), 100);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.EARTH), 100);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.FIRE), 100);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.WIND), 100);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.POISON), 50);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.HOLY), 100);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.SHADOW), 0);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.GHOST), 100);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.UNDEAD), 0);

		// LEVEL 2
		elementMap.put(new ElementKey(Element.NORMAL, Element.NORMAL_2), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.WATER_2), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.EARTH_2), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.FIRE_2), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.WIND_2), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.POISON_2), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.HOLY_2), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.SHADOW_2), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.GHOST_2), 25);
		elementMap.put(new ElementKey(Element.NORMAL, Element.UNDEAD_2), 100);

		elementMap.put(new ElementKey(Element.WATER, Element.NORMAL_2), 100);
		elementMap.put(new ElementKey(Element.WATER, Element.WATER_2), 0);
		elementMap.put(new ElementKey(Element.WATER, Element.EARTH_2), 100);
		elementMap.put(new ElementKey(Element.WATER, Element.FIRE_2), 175);
		elementMap.put(new ElementKey(Element.WATER, Element.WIND_2), 25);
		elementMap.put(new ElementKey(Element.WATER, Element.POISON_2), 100);
		elementMap.put(new ElementKey(Element.WATER, Element.HOLY_2), 50);
		elementMap.put(new ElementKey(Element.WATER, Element.SHADOW_2), 75);
		elementMap.put(new ElementKey(Element.WATER, Element.GHOST_2), 100);
		elementMap.put(new ElementKey(Element.WATER, Element.UNDEAD_2), 100);

		elementMap.put(new ElementKey(Element.EARTH, Element.NORMAL_2), 100);
		elementMap.put(new ElementKey(Element.EARTH, Element.WATER_2), 100);
		elementMap.put(new ElementKey(Element.EARTH, Element.EARTH_2), 50);
		elementMap.put(new ElementKey(Element.EARTH, Element.FIRE_2), 25);
		elementMap.put(new ElementKey(Element.EARTH, Element.WIND_2), 175);
		elementMap.put(new ElementKey(Element.EARTH, Element.POISON_2), 100);
		elementMap.put(new ElementKey(Element.EARTH, Element.HOLY_2), 50);
		elementMap.put(new ElementKey(Element.EARTH, Element.SHADOW_2), 75);
		elementMap.put(new ElementKey(Element.EARTH, Element.GHOST_2), 100);
		elementMap.put(new ElementKey(Element.EARTH, Element.UNDEAD_2), 100);

		elementMap.put(new ElementKey(Element.FIRE, Element.NORMAL_2), 100);
		elementMap.put(new ElementKey(Element.FIRE, Element.WATER_2), 25);
		elementMap.put(new ElementKey(Element.FIRE, Element.EARTH_2), 175);
		elementMap.put(new ElementKey(Element.FIRE, Element.FIRE_2), 0);
		elementMap.put(new ElementKey(Element.FIRE, Element.WIND_2), 100);
		elementMap.put(new ElementKey(Element.FIRE, Element.POISON_2), 100);
		elementMap.put(new ElementKey(Element.FIRE, Element.HOLY_2), 50);
		elementMap.put(new ElementKey(Element.FIRE, Element.SHADOW_2), 75);
		elementMap.put(new ElementKey(Element.FIRE, Element.GHOST_2), 100);
		elementMap.put(new ElementKey(Element.FIRE, Element.UNDEAD_2), 150);

		elementMap.put(new ElementKey(Element.WIND, Element.NORMAL_2), 100);
		elementMap.put(new ElementKey(Element.WIND, Element.WATER_2), 175);
		elementMap.put(new ElementKey(Element.WIND, Element.EARTH_2), 25);
		elementMap.put(new ElementKey(Element.WIND, Element.FIRE_2), 100);
		elementMap.put(new ElementKey(Element.WIND, Element.WIND_2), 0);
		elementMap.put(new ElementKey(Element.WIND, Element.POISON_2), 100);
		elementMap.put(new ElementKey(Element.WIND, Element.HOLY_2), 50);
		elementMap.put(new ElementKey(Element.WIND, Element.SHADOW_2), 75);
		elementMap.put(new ElementKey(Element.WIND, Element.GHOST_2), 100);
		elementMap.put(new ElementKey(Element.WIND, Element.UNDEAD_2), 100);

		elementMap.put(new ElementKey(Element.POISON, Element.NORMAL_2), 100);
		elementMap.put(new ElementKey(Element.POISON, Element.WATER_2), 75);
		elementMap.put(new ElementKey(Element.POISON, Element.EARTH_2), 125);
		elementMap.put(new ElementKey(Element.POISON, Element.FIRE_2), 125);
		elementMap.put(new ElementKey(Element.POISON, Element.WIND_2), 125);
		elementMap.put(new ElementKey(Element.POISON, Element.POISON_2), 0);
		elementMap.put(new ElementKey(Element.POISON, Element.HOLY_2), 50);
		elementMap.put(new ElementKey(Element.POISON, Element.SHADOW_2), 25);
		elementMap.put(new ElementKey(Element.POISON, Element.GHOST_2), 75);
		elementMap.put(new ElementKey(Element.POISON, Element.UNDEAD_2), -50);

		elementMap.put(new ElementKey(Element.HOLY, Element.HOLY_2), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.WATER_2), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.EARTH_2), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.FIRE_2), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.WIND_2), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.POISON_2), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.HOLY_2), -25);
		elementMap.put(new ElementKey(Element.HOLY, Element.SHADOW_2), 150);
		elementMap.put(new ElementKey(Element.HOLY, Element.GHOST_2), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.UNDEAD_2), 175);

		elementMap.put(new ElementKey(Element.SHADOW, Element.NORMAL_2), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.WATER_2), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.EARTH_2), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.FIRE_2), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.WIND_2), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.POISON_2), 25);
		elementMap.put(new ElementKey(Element.SHADOW, Element.HOLY_2), 150);
		elementMap.put(new ElementKey(Element.SHADOW, Element.SHADOW_2), -25);
		elementMap.put(new ElementKey(Element.SHADOW, Element.GHOST_2), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.UNDEAD_2), -50);

		elementMap.put(new ElementKey(Element.GHOST, Element.NORMAL_2), 0);
		elementMap.put(new ElementKey(Element.GHOST, Element.WATER_2), 75);
		elementMap.put(new ElementKey(Element.GHOST, Element.EARTH_2), 75);
		elementMap.put(new ElementKey(Element.GHOST, Element.FIRE_2), 75);
		elementMap.put(new ElementKey(Element.GHOST, Element.WIND_2), 75);
		elementMap.put(new ElementKey(Element.GHOST, Element.POISON_2), 75);
		elementMap.put(new ElementKey(Element.GHOST, Element.HOLY_2), 50);
		elementMap.put(new ElementKey(Element.GHOST, Element.SHADOW_2), 50);
		elementMap.put(new ElementKey(Element.GHOST, Element.GHOST_2), 150);
		elementMap.put(new ElementKey(Element.GHOST, Element.UNDEAD_2), 125);

		elementMap.put(new ElementKey(Element.UNDEAD, Element.NORMAL_2), 100);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.WATER_2), 75);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.EARTH_2), 75);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.FIRE_2), 75);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.WIND_2), 75);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.POISON_2), 25);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.HOLY_2), 125);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.SHADOW_2), 0);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.GHOST_2), 100);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.UNDEAD_2), 0);

		// LEVEL 3
		elementMap.put(new ElementKey(Element.NORMAL, Element.NORMAL_3), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.WATER_3), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.EARTH_3), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.FIRE_3), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.WIND_3), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.POISON_3), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.HOLY_3), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.SHADOW_3), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.GHOST_3), 0);
		elementMap.put(new ElementKey(Element.NORMAL, Element.UNDEAD_3), 100);

		elementMap.put(new ElementKey(Element.WATER, Element.NORMAL_3), 100);
		elementMap.put(new ElementKey(Element.WATER, Element.WATER_3), -25);
		elementMap.put(new ElementKey(Element.WATER, Element.EARTH_3), 100);
		elementMap.put(new ElementKey(Element.WATER, Element.FIRE_3), 200);
		elementMap.put(new ElementKey(Element.WATER, Element.WIND_3), 0);
		elementMap.put(new ElementKey(Element.WATER, Element.POISON_3), 100);
		elementMap.put(new ElementKey(Element.WATER, Element.HOLY_3), 25);
		elementMap.put(new ElementKey(Element.WATER, Element.SHADOW_3), 50);
		elementMap.put(new ElementKey(Element.WATER, Element.GHOST_3), 100);
		elementMap.put(new ElementKey(Element.WATER, Element.UNDEAD_3), 125);

		elementMap.put(new ElementKey(Element.EARTH, Element.NORMAL_3), 100);
		elementMap.put(new ElementKey(Element.EARTH, Element.WATER_3), 100);
		elementMap.put(new ElementKey(Element.EARTH, Element.EARTH_3), 0);
		elementMap.put(new ElementKey(Element.EARTH, Element.FIRE_3), 0);
		elementMap.put(new ElementKey(Element.EARTH, Element.WIND_3), 200);
		elementMap.put(new ElementKey(Element.EARTH, Element.POISON_3), 100);
		elementMap.put(new ElementKey(Element.EARTH, Element.HOLY_3), 25);
		elementMap.put(new ElementKey(Element.EARTH, Element.SHADOW_3), 50);
		elementMap.put(new ElementKey(Element.EARTH, Element.GHOST_3), 100);
		elementMap.put(new ElementKey(Element.EARTH, Element.UNDEAD_3), 75);

		elementMap.put(new ElementKey(Element.FIRE, Element.NORMAL_3), 100);
		elementMap.put(new ElementKey(Element.FIRE, Element.WATER_3), 0);
		elementMap.put(new ElementKey(Element.FIRE, Element.EARTH_3), 200);
		elementMap.put(new ElementKey(Element.FIRE, Element.FIRE_3), -25);
		elementMap.put(new ElementKey(Element.FIRE, Element.WIND_3), 100);
		elementMap.put(new ElementKey(Element.FIRE, Element.POISON_3), 100);
		elementMap.put(new ElementKey(Element.FIRE, Element.HOLY_3), 25);
		elementMap.put(new ElementKey(Element.FIRE, Element.SHADOW_3), 50);
		elementMap.put(new ElementKey(Element.FIRE, Element.GHOST_3), 100);
		elementMap.put(new ElementKey(Element.FIRE, Element.UNDEAD_3), 175);

		elementMap.put(new ElementKey(Element.WIND, Element.NORMAL_3), 100);
		elementMap.put(new ElementKey(Element.WIND, Element.WATER_3), 200);
		elementMap.put(new ElementKey(Element.WIND, Element.EARTH_3), 0);
		elementMap.put(new ElementKey(Element.WIND, Element.FIRE_3), 100);
		elementMap.put(new ElementKey(Element.WIND, Element.WIND_3), -25);
		elementMap.put(new ElementKey(Element.WIND, Element.POISON_3), 100);
		elementMap.put(new ElementKey(Element.WIND, Element.HOLY_3), 25);
		elementMap.put(new ElementKey(Element.WIND, Element.SHADOW_3), 50);
		elementMap.put(new ElementKey(Element.WIND, Element.GHOST_3), 100);
		elementMap.put(new ElementKey(Element.WIND, Element.UNDEAD_3), 100);

		elementMap.put(new ElementKey(Element.POISON, Element.NORMAL_3), 100);
		elementMap.put(new ElementKey(Element.POISON, Element.WATER_3), 50);
		elementMap.put(new ElementKey(Element.POISON, Element.EARTH_3), 100);
		elementMap.put(new ElementKey(Element.POISON, Element.FIRE_3), 100);
		elementMap.put(new ElementKey(Element.POISON, Element.WIND_3), 100);
		elementMap.put(new ElementKey(Element.POISON, Element.POISON_3), 0);
		elementMap.put(new ElementKey(Element.POISON, Element.HOLY_3), 25);
		elementMap.put(new ElementKey(Element.POISON, Element.SHADOW_3), 0);
		elementMap.put(new ElementKey(Element.POISON, Element.GHOST_3), 50);
		elementMap.put(new ElementKey(Element.POISON, Element.UNDEAD_3), -75);

		elementMap.put(new ElementKey(Element.HOLY, Element.HOLY_3), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.WATER_3), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.EARTH_3), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.FIRE_3), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.WIND_3), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.POISON_3), 125);
		elementMap.put(new ElementKey(Element.HOLY, Element.HOLY_3), -50);
		elementMap.put(new ElementKey(Element.HOLY, Element.SHADOW_3), 175);
		elementMap.put(new ElementKey(Element.HOLY, Element.GHOST_3), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.UNDEAD_3), 200);

		elementMap.put(new ElementKey(Element.SHADOW, Element.NORMAL_3), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.WATER_3), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.EARTH_3), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.FIRE_3), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.WIND_3), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.POISON_3), 0);
		elementMap.put(new ElementKey(Element.SHADOW, Element.HOLY_3), 175);
		elementMap.put(new ElementKey(Element.SHADOW, Element.SHADOW_3), -50);
		elementMap.put(new ElementKey(Element.SHADOW, Element.GHOST_3), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.UNDEAD_3), -75);

		elementMap.put(new ElementKey(Element.GHOST, Element.NORMAL_3), 0);
		elementMap.put(new ElementKey(Element.GHOST, Element.WATER_3), 50);
		elementMap.put(new ElementKey(Element.GHOST, Element.EARTH_3), 50);
		elementMap.put(new ElementKey(Element.GHOST, Element.FIRE_3), 50);
		elementMap.put(new ElementKey(Element.GHOST, Element.WIND_3), 50);
		elementMap.put(new ElementKey(Element.GHOST, Element.POISON_3), 50);
		elementMap.put(new ElementKey(Element.GHOST, Element.HOLY_3), 25);
		elementMap.put(new ElementKey(Element.GHOST, Element.SHADOW_3), 25);
		elementMap.put(new ElementKey(Element.GHOST, Element.GHOST_3), 175);
		elementMap.put(new ElementKey(Element.GHOST, Element.UNDEAD_3), 150);

		elementMap.put(new ElementKey(Element.UNDEAD, Element.NORMAL_3), 100);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.WATER_3), 50);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.EARTH_3), 50);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.FIRE_3), 50);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.WIND_3), 50);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.POISON_3), 0);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.HOLY_3), 150);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.SHADOW_3), 0);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.GHOST_3), 100);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.UNDEAD_3), 0);

		// LEVEL 4
		elementMap.put(new ElementKey(Element.NORMAL, Element.NORMAL_4), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.WATER_4), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.EARTH_4), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.FIRE_4), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.WIND_4), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.POISON_4), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.HOLY_4), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.SHADOW_4), 100);
		elementMap.put(new ElementKey(Element.NORMAL, Element.GHOST_4), 0);
		elementMap.put(new ElementKey(Element.NORMAL, Element.UNDEAD_4), 100);

		elementMap.put(new ElementKey(Element.WATER, Element.NORMAL_4), 100);
		elementMap.put(new ElementKey(Element.WATER, Element.WATER_4), -50);
		elementMap.put(new ElementKey(Element.WATER, Element.EARTH_4), 100);
		elementMap.put(new ElementKey(Element.WATER, Element.FIRE_4), 200);
		elementMap.put(new ElementKey(Element.WATER, Element.WIND_4), 0);
		elementMap.put(new ElementKey(Element.WATER, Element.POISON_4), 75);
		elementMap.put(new ElementKey(Element.WATER, Element.HOLY_4), 0);
		elementMap.put(new ElementKey(Element.WATER, Element.SHADOW_4), 25);
		elementMap.put(new ElementKey(Element.WATER, Element.GHOST_4), 100);
		elementMap.put(new ElementKey(Element.WATER, Element.UNDEAD_4), 150);

		elementMap.put(new ElementKey(Element.EARTH, Element.NORMAL_4), 100);
		elementMap.put(new ElementKey(Element.EARTH, Element.WATER_4), 100);
		elementMap.put(new ElementKey(Element.EARTH, Element.EARTH_4), -25);
		elementMap.put(new ElementKey(Element.EARTH, Element.FIRE_4), 0);
		elementMap.put(new ElementKey(Element.EARTH, Element.WIND_4), 200);
		elementMap.put(new ElementKey(Element.EARTH, Element.POISON_4), 75);
		elementMap.put(new ElementKey(Element.EARTH, Element.HOLY_4), 0);
		elementMap.put(new ElementKey(Element.EARTH, Element.SHADOW_4), 25);
		elementMap.put(new ElementKey(Element.EARTH, Element.GHOST_4), 100);
		elementMap.put(new ElementKey(Element.EARTH, Element.UNDEAD_4), 50);

		elementMap.put(new ElementKey(Element.FIRE, Element.NORMAL_4), 100);
		elementMap.put(new ElementKey(Element.FIRE, Element.WATER_4), 0);
		elementMap.put(new ElementKey(Element.FIRE, Element.EARTH_4), 200);
		elementMap.put(new ElementKey(Element.FIRE, Element.FIRE_4), -50);
		elementMap.put(new ElementKey(Element.FIRE, Element.WIND_4), 100);
		elementMap.put(new ElementKey(Element.FIRE, Element.POISON_4), 75);
		elementMap.put(new ElementKey(Element.FIRE, Element.HOLY_4), 0);
		elementMap.put(new ElementKey(Element.FIRE, Element.SHADOW_4), 25);
		elementMap.put(new ElementKey(Element.FIRE, Element.GHOST_4), 100);
		elementMap.put(new ElementKey(Element.FIRE, Element.UNDEAD_4), 200);

		elementMap.put(new ElementKey(Element.WIND, Element.NORMAL_4), 100);
		elementMap.put(new ElementKey(Element.WIND, Element.WATER_4), 200);
		elementMap.put(new ElementKey(Element.WIND, Element.EARTH_4), 0);
		elementMap.put(new ElementKey(Element.WIND, Element.FIRE_4), 100);
		elementMap.put(new ElementKey(Element.WIND, Element.WIND_4), -50);
		elementMap.put(new ElementKey(Element.WIND, Element.POISON_4), 75);
		elementMap.put(new ElementKey(Element.WIND, Element.HOLY_4), 0);
		elementMap.put(new ElementKey(Element.WIND, Element.SHADOW_4), 25);
		elementMap.put(new ElementKey(Element.WIND, Element.GHOST_4), 100);
		elementMap.put(new ElementKey(Element.WIND, Element.UNDEAD_4), 100);

		elementMap.put(new ElementKey(Element.POISON, Element.NORMAL_4), 100);
		elementMap.put(new ElementKey(Element.POISON, Element.WATER_4), 25);
		elementMap.put(new ElementKey(Element.POISON, Element.EARTH_4), 75);
		elementMap.put(new ElementKey(Element.POISON, Element.FIRE_4), 75);
		elementMap.put(new ElementKey(Element.POISON, Element.WIND_4), 75);
		elementMap.put(new ElementKey(Element.POISON, Element.POISON_4), 0);
		elementMap.put(new ElementKey(Element.POISON, Element.HOLY_4), 0);
		elementMap.put(new ElementKey(Element.POISON, Element.SHADOW_4), -25);
		elementMap.put(new ElementKey(Element.POISON, Element.GHOST_4), 25);
		elementMap.put(new ElementKey(Element.POISON, Element.UNDEAD_4), -100);

		elementMap.put(new ElementKey(Element.HOLY, Element.HOLY_4), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.WATER_4), 75);
		elementMap.put(new ElementKey(Element.HOLY, Element.EARTH_4), 75);
		elementMap.put(new ElementKey(Element.HOLY, Element.FIRE_4), 75);
		elementMap.put(new ElementKey(Element.HOLY, Element.WIND_4), 75);
		elementMap.put(new ElementKey(Element.HOLY, Element.POISON_4), 125);
		elementMap.put(new ElementKey(Element.HOLY, Element.HOLY_4), -100);
		elementMap.put(new ElementKey(Element.HOLY, Element.SHADOW_4), 200);
		elementMap.put(new ElementKey(Element.HOLY, Element.GHOST_4), 100);
		elementMap.put(new ElementKey(Element.HOLY, Element.UNDEAD_4), 200);

		elementMap.put(new ElementKey(Element.SHADOW, Element.NORMAL_4), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.WATER_4), 75);
		elementMap.put(new ElementKey(Element.SHADOW, Element.EARTH_4), 75);
		elementMap.put(new ElementKey(Element.SHADOW, Element.FIRE_4), 75);
		elementMap.put(new ElementKey(Element.SHADOW, Element.WIND_4), 75);
		elementMap.put(new ElementKey(Element.SHADOW, Element.POISON_4), -25);
		elementMap.put(new ElementKey(Element.SHADOW, Element.HOLY_4), 200);
		elementMap.put(new ElementKey(Element.SHADOW, Element.SHADOW_4), -100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.GHOST_4), 100);
		elementMap.put(new ElementKey(Element.SHADOW, Element.UNDEAD_4), -100);

		elementMap.put(new ElementKey(Element.GHOST, Element.NORMAL_4), 0);
		elementMap.put(new ElementKey(Element.GHOST, Element.WATER_4), 25);
		elementMap.put(new ElementKey(Element.GHOST, Element.EARTH_4), 25);
		elementMap.put(new ElementKey(Element.GHOST, Element.FIRE_4), 25);
		elementMap.put(new ElementKey(Element.GHOST, Element.WIND_4), 25);
		elementMap.put(new ElementKey(Element.GHOST, Element.POISON_4), 25);
		elementMap.put(new ElementKey(Element.GHOST, Element.HOLY_4), 0);
		elementMap.put(new ElementKey(Element.GHOST, Element.SHADOW_4), 0);
		elementMap.put(new ElementKey(Element.GHOST, Element.GHOST_4), 200);
		elementMap.put(new ElementKey(Element.GHOST, Element.UNDEAD_4), 175);

		elementMap.put(new ElementKey(Element.UNDEAD, Element.NORMAL_4), 100);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.WATER_4), 25);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.EARTH_4), 25);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.FIRE_4), 25);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.WIND_4), 25);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.POISON_4), -25);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.HOLY_4), 175);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.SHADOW_4), 0);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.GHOST_4), 100);
		elementMap.put(new ElementKey(Element.UNDEAD, Element.UNDEAD_4), 0);

		// Define the legal attack elements.
		legalAttackElements.add(Element.EARTH);
		legalAttackElements.add(Element.FIRE);
		legalAttackElements.add(Element.GHOST);
		legalAttackElements.add(Element.HOLY);
		legalAttackElements.add(Element.NORMAL);
		legalAttackElements.add(Element.POISON);
		legalAttackElements.add(Element.SHADOW);
		legalAttackElements.add(Element.UNDEAD);
		legalAttackElements.add(Element.WATER);
		legalAttackElements.add(Element.WIND);
	}

	/**
	 * Dont instantiate this class. Use static accessor methods.
	 */
	private ElementModifier() {
		// no op.
	}

	/**
	 * Returns the damage modifier for a given attacker element and defender
	 * element. Attack element must always be of level 1.
	 * 
	 * @param attacker
	 *            Element of the attacker.
	 * @param defender
	 *            Element of the defender.
	 * @return The damage modifier.
	 */
	static int getModifier(Element attacker, Element defender) {

		if (!legalAttackElements.contains(attacker)) {
			throw new IllegalArgumentException("Attack element must always be level 1.");
		}

		final ElementKey key = new ElementKey(attacker, defender);
		return elementMap.get(key);
	}

	/**
	 * Alias of {@link #getModifier(Element, Element)} but returns the value as
	 * a float value (e.g. 1.25 instead of 125).
	 * 
	 * @param attacker
	 *            Element of the attacker. Must be level 1 element.
	 * @param defender
	 *            Element of the defender.
	 * @return The damage modifier.
	 */
	static float getModifierFloat(Element attacker, Element defender) {
		return getModifier(attacker, defender) / 100f;
	}

}
