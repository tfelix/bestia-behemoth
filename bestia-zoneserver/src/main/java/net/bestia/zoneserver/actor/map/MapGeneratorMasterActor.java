package net.bestia.zoneserver.actor.map;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.hazelcast.internal.util.ThreadLocalRandom;

import akka.actor.ActorIdentity;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Identify;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import de.tfelix.bestia.worldgen.description.MapDescription;
import de.tfelix.bestia.worldgen.io.NodeConnector;
import de.tfelix.bestia.worldgen.map.MapPart;
import de.tfelix.bestia.worldgen.message.WorkstateMessage;
import net.bestia.model.domain.MapParameter;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.configuration.MaintenanceLevel;
import net.bestia.zoneserver.configuration.RuntimeConfigurationService;
import net.bestia.zoneserver.map.generator.MapGeneratorMasterService;
import net.bestia.zoneserver.service.LoginService;
import scala.concurrent.duration.Duration;

@Component
@Scope("prototype")
public class MapGeneratorMasterActor extends BestiaActor {

	/**
	 * Interface implementation to talk to the clients.
	 *
	 */
	private class AkkaMapGenClient implements NodeConnector {

		private final ActorRef generatorNode;

		public AkkaMapGenClient(ActorRef generatorNode) {

			this.generatorNode = Objects.requireNonNull(generatorNode);
		}

		@Override
		public void sendClient(MapPart part) {
			generatorNode.tell(part, getSelf());
		}

		@Override
		public void sendClient(MapDescription desc) {
			generatorNode.tell(desc, getSelf());
		}

		@Override
		public void startWorkload(String label) {
			generatorNode.tell(label, getSelf());
		}
	}

	public final static String NAME = "mapGeneratorMaster";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final static String START_MSG = "start";
	private final static String FINISH_MSG = "finished";

	private final MapGeneratorMasterService mapGenService;
	private MapParameter mapBaseParameter = null;

	private int currentLookupIdent = 0;
	private Set<ActorRef> availableNodes = new HashSet<>();

	private final RuntimeConfigurationService clusterConfig;
	private final LoginService loginService;

	@Autowired
	public MapGeneratorMasterActor(
			MapGeneratorMasterService mapGenService,
			LoginService loginService,
			RuntimeConfigurationService config) {

		this.mapGenService = Objects.requireNonNull(mapGenService);
		this.clusterConfig = Objects.requireNonNull(config);
		this.loginService = Objects.requireNonNull(loginService);

		// Setup a call to the finish method. Must use akka messaging in order
		// to prevent race conditions.
		mapGenService.setOnFinishCallback(new Runnable() {

			@Override
			public void run() {
				self().tell(FINISH_MSG, getSelf());
			}
		});
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(MapParameter.class, this::handleMapParameter)
				.match(WorkstateMessage.class, mapGenService::consumeNodeMessage)
				.match(ActorIdentity.class, this::addToAvailableNodes)
				.matchEquals(START_MSG, m -> this.start())
				.matchEquals(FINISH_MSG, m -> this.finish())
				.build();
	}

	private void handleMapParameter(MapParameter params) {
		LOG.info("Received map base parameter. Starting to generate map. ({})", params);
		LOG.info("Putting server into maintenance mode and disconnecting all users.");

		clusterConfig.setMaintenanceMode(MaintenanceLevel.FULL);
		loginService.logoutAll();

		mapBaseParameter = params;
		queryGeneratorNodes();
	}

	private void start() {
		LOG.debug("Queried all generator nodes. Starting to generate map.");

		// Prepare the list of nodes.
		List<NodeConnector> nodes = availableNodes.stream()
				.map(ref -> new AkkaMapGenClient(ref))
				.collect(Collectors.toList());

		if (nodes.size() == 0) {
			LOG.warning("No other nodes found to generate the map. Aborting.");
			finish();
			return;
		}

		mapGenService.generateMap(mapBaseParameter, nodes);
	}

	/**
	 * Map was generated.
	 */
	private void finish() {
		LOG.info("Map generation was finished. Ending maintenance mode.");
		clusterConfig.setMaintenanceMode(MaintenanceLevel.NONE);
	}

	/**
	 * Adds the answering nodes to the generation algorithm.
	 */
	private void addToAvailableNodes(ActorIdentity msg) {

		if (!msg.correlationId().equals(currentLookupIdent)) {
			// not the current lookup.
			return;
		}

		if (msg.getActorRef().isPresent()) {
			LOG.debug("Map generator client node identified: {}", msg.getActorRef().get());
			availableNodes.add(msg.getActorRef().get());
		}
	}

	/**
	 * Tries to lookup all generator nodes in the system.
	 */
	private void queryGeneratorNodes() {
		LOG.debug("Quering available map generator nodes.");

		availableNodes.clear();
		currentLookupIdent = ThreadLocalRandom.current().nextInt();
		final ActorSelection selection = context()
				.actorSelection(AkkaCluster.getNodeName(MapGeneratorClientActor.NAME));
		selection.tell(new Identify(currentLookupIdent), getSelf());

		// Wait three seconds until generation starts.
		context().system().scheduler().scheduleOnce(
				Duration.create(3, TimeUnit.SECONDS),
				getSelf(),
				START_MSG,
				context().dispatcher(),
				null);
	}

}
