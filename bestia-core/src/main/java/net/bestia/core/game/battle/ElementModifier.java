package net.bestia.core.game.battle;

import java.util.HashMap;
import java.util.Map;

/**
 * Returns the attack damage modifier for a given elemental set of the attacker
 * and defender.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public final class ElementModifier {
	
	/**
	 * Wraps the creation of element keys to retrieve the element modifier.
	 * 
	 * @author Thomas Felix <thomas.felix@tfelix.de>
	 *
	 */
	private static class ElementKey {
		private final Element el1;
		private final Element el2;
		
		public ElementKey(Element el1, Element el2) {
			this.el1 = el1;
			this.el2 = el2;
		}
		
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
	        if (!(o instanceof ElementKey)) return false;
	        ElementKey key = (ElementKey) o;
	        return el1 == key.el1 && el2 == key.el2;
		}
		
		@Override
	    public int hashCode() {
	        int result = el1.hashCode();
	        result = 31 * result + el2.hashCode();
	        return result;
	    }
	}
	
	private final static Map<ElementKey, Double> elementMap = new HashMap<ElementKey, Double>();
	
	/**
	 * Returns the damage modifier for a given attacker element and defender element.
	 * 
	 * @param attacker Element of the attacker.
	 * @param defender Element of the defender.
	 * @return
	 */
	public static double getModifier(Element attacker, Element defender) {
		ElementKey key = new ElementKey(attacker, defender);
		return elementMap.get(key);
	}
}
