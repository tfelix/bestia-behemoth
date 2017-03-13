package net.bestia.model.map;

/**
 * Since the walkpeed in bestia can have different meanings and representations
 * (one encoded as float, the other as an int for transmission reasions) the
 * walkspeed are completly encapsulated as a own class.
 * 
 * @TODO Testen.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Walkspeed {

	private static final float MAX_WALKSPEED = 3.5f;

	private float speed;

	private Walkspeed(float speed) {
		if (speed < 0 || speed > MAX_WALKSPEED / 1000) {

		}

		this.speed = (int) (speed * MAX_WALKSPEED);
	}

	private Walkspeed(int speed) {
		if (speed < 0 || speed > 3500) {
			throw new IllegalArgumentException("Walkspeed in int form must be between 0 and 3500");
		}

		this.speed = speed;
	}

	public static Walkspeed fromInt(int speed) {
		return new Walkspeed(speed);
	}

	public static Walkspeed fromFloat(float speed) {
		return new Walkspeed(speed);
	}

	public float getSpeed() {
		return speed;
	}

	/**
	 * Returns the walkspeed as float.
	 * 
	 * @return
	 */
	public float toInt() {
		return (int) (speed * 100);
	}

}
