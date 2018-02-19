package bestia.model.map;

/**
 * Since the walkpeed in bestia can have different meanings and representations
 * (one encoded as float, the other as an int for transmission reason) the
 * walkspeed are completly encapsulated as a own class.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Walkspeed {

	public static final float MAX_WALKSPEED = 3.5f;
	public static final int MAX_WALKSPEED_INT = (int) (MAX_WALKSPEED * 100);
	
	public static final Walkspeed ZERO = new Walkspeed(0);

	private final float speed;

	/**
	 * Generates a walkspeed from a float value.
	 * 
	 * @param speed
	 *            The current walkspeed in float.
	 */
	private Walkspeed(float speed) {
		if (speed < 0 || speed > MAX_WALKSPEED) {
			throw new IllegalArgumentException("Walkspeed in float form must be between 0 and 3.5f");
		}

		this.speed = speed;
	}

	private Walkspeed(int speed) {
		if (speed < 0 || speed > MAX_WALKSPEED_INT) {
			throw new IllegalArgumentException("Walkspeed in int form must be between 0 and 3500");
		}

		this.speed = speed / 100.0f;
	}

	/**
	 * Generates the walkspeed from an integer value. Value must be between 0
	 * and {@link #MAX_WALKSPEED_INT}.
	 * 
	 * @param speed
	 *            The speed.
	 * @return A walkspeed object.
	 */
	public static Walkspeed fromInt(int speed) {
		return new Walkspeed(speed);
	}

	public static Walkspeed fromFloat(float speed) {
		return new Walkspeed(speed);
	}

	/**
	 * Gets the current speed.
	 * 
	 * @return The current walkspeed.
	 */
	public float getSpeed() {
		return speed;
	}

	/**
	 * Returns the walkspeed as float.
	 * 
	 * @return The current walkspeed as int.
	 */
	public int toInt() {
		return (int) (speed * 100);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Float.floatToIntBits(speed);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Walkspeed other = (Walkspeed) obj;
		if (Float.floatToIntBits(speed) != Float.floatToIntBits(other.speed))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return String.format("Walkspeed[spd: %.2f]", getSpeed());
	}
}
