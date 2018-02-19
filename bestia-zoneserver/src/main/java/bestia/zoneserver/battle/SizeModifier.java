package bestia.zoneserver.battle;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Different weapons deal different damage towards different sized bestias.
 * 
 * @author Thomas Felix
 *
 */
public final class SizeModifier {

	/**
	 * Wraps the creation of size keys to retrieve the size modifier.
	 *
	 */
	private static class SizeKey {
		private final Size s1;
		private final Size s2;

		public SizeKey(Size s1, Size s2) {
			this.s1 = Objects.requireNonNull(s1);
			this.s2 = Objects.requireNonNull(s2);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof SizeKey))
				return false;
			SizeKey key = (SizeKey) o;
			return s1 == key.s1 && s2 == key.s2;
		}

		@Override
		public int hashCode() {
			return 31 * s1.hashCode() + s2.hashCode();
		}
	}

	private final static Map<SizeKey, Float> sizeMap = new HashMap<>();
	
	static {
		sizeMap.put(new SizeKey(Size.SMALL, Size.SMALL), 1.3f);
		sizeMap.put(new SizeKey(Size.MEDIUM, Size.SMALL), 1f);
		sizeMap.put(new SizeKey(Size.BIG, Size.SMALL), 0.75f);
		
		sizeMap.put(new SizeKey(Size.SMALL, Size.MEDIUM), 1f);
		sizeMap.put(new SizeKey(Size.MEDIUM, Size.MEDIUM), 1.15f);
		sizeMap.put(new SizeKey(Size.BIG, Size.MEDIUM), 1f);
		
		sizeMap.put(new SizeKey(Size.SMALL, Size.BIG), 0.7f);
		sizeMap.put(new SizeKey(Size.MEDIUM, Size.BIG), 1.1f);
		sizeMap.put(new SizeKey(Size.BIG, Size.BIG), 1.3f);
	}

	private SizeModifier() {
		// no op.
	}

	/**
	 * Returns the damage value modifier as a float value (e.g. 1.25).
	 * 
	 * @param attacker
	 *            Size of the attacker.
	 * @param defender
	 *            Size of the defender.
	 * @return The damage modifier.
	 */
	public static float getModifierFloat(Size attacker, Size defender) {
		
		final SizeKey sk = new SizeKey(attacker, defender);		
		return sizeMap.getOrDefault(sk, 1.0f);
	}
}
