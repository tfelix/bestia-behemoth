package net.bestia.model.map;

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

	private float speed;

	/**
	 * Generates a walkspeed from a float value.
	 * 
	 * @param speed
	 *            The current walkspeed in float.
	 */
	private Walkspeed(float speed) {
		if (speed < 0 || speed > MAX_WALKSPEED / 1000) {

		}

		this.speed = (int) (speed * MAX_WALKSPEED);
	}

	private Walkspeed(int speed) {
		if (speed < 0 || speed > MAX_WALKSPEED_INT) {
			throw new IllegalArgumentException("Walkspeed in int form must be between 0 and 3500");
		}

		this.speed = speed;
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

}
