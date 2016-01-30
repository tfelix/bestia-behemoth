package net.bestia.zoneserver.ecs.system;

import java.util.HashMap;
import java.util.Map;

import com.artemis.Aspect;
import com.artemis.Aspect.Builder;
import com.artemis.AspectSubscriptionManager;
import com.artemis.EntitySubscription;
import com.artemis.systems.IteratingSystem;
import com.artemis.utils.IntBag;

import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.PlayerBestia;

/**
 * This helper class can subscribe to various entities. Which can be called
 * later it helps to reduce boilerplate code when subscribing.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class IteratingSubscriptionSystem extends IteratingSystem {

	public static final String ACTIVE_PLAYER_SUBSCRIPTION = "activePlayers";

	private boolean wasInitCalled = false;
	private final Map<String, EntitySubscription> subscriptions = new HashMap<>();

	public IteratingSubscriptionSystem(Builder aspect) {
		super(aspect);
		// no op.
	}

	@Override
	protected void initialize() {
		super.initialize();

		wasInitCalled = true;
	}

	/**
	 * Helper method to directly subscribe to player entities. They are saved
	 * under the ident "activePlayers".
	 */
	protected void subscribeActivePlayers() {
		subscribe(ACTIVE_PLAYER_SUBSCRIPTION, Aspect.all(Active.class, PlayerBestia.class));
	}

	protected void subscribe(String ident, Builder aspects) {

		if (!wasInitCalled) {
			throw new IllegalStateException("initialize() was not called. Method must be called before!");
		}

		AspectSubscriptionManager asm = world.getAspectSubscriptionManager();
		final EntitySubscription subs = asm.get(aspects);
		subscriptions.put(ident, subs);
	}

	protected IntBag getEntities(String subscriptionIdent) {
		return subscriptions.get(subscriptionIdent).getEntities();
	}

	@Override
	protected void process(int entityId) {
		// no op.
	}
}
