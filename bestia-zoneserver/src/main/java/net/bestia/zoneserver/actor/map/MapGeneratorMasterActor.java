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
import de.tfelix.bestia.worldgen.io.MasterCom;
import de.tfelix.bestia.worldgen.map.MapPart;
import de.tfelix.bestia.worldgen.message.WorkstateMessage;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.map.MapBaseParameter;
import net.bestia.zoneserver.service.MapGeneratorMasterService;
import scala.concurrent.duration.Duration;

@Component
@Scope("prototype")
public class MapGeneratorMasterActor extends BestiaActor {

	/**
	 * Interface implementation to talk to the clients.
	 *
	 */
	private class AkkaMapGenClient implements MasterCom {

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

	private final MapGeneratorMasterService mapGenService;
	private MapBaseParameter mapBaseParameter = null;

	private int currentLookupIdent = 0;
	private Set<ActorRef> availableNodes = new HashSet<>();

	@Autowired
	public MapGeneratorMasterActor(MapGeneratorMasterService mapGenService) {

		this.mapGenService = Objects.requireNonNull(mapGenService);
	}

	@Override
	public void onReceive(Object msg) throws Throwable {

		if (msg instanceof MapBaseParameter) {

			LOG.info("Received map base parameter. Starting to generate map. ({})", msg);
			mapBaseParameter = (MapBaseParameter) msg;
			queryGeneratorNodes();

		} else if(msg instanceof WorkstateMessage) {
			
			mapGenService.consumeNodeMessage((WorkstateMessage) msg);
			
		} else if (msg instanceof ActorIdentity) {

			addToAvailableNodes((ActorIdentity) msg);

		} else if (msg.equals(START_MSG)) {

			// Prepare the list of nodes.
			List<MasterCom> nodes = availableNodes.stream()
					.map(ref -> new AkkaMapGenClient(ref))
					.collect(Collectors.toList());
			
			if(nodes.size() == 0) {
				LOG.warning("No other nodes found to generate the map. Aborting.");
				return;
			}

			mapGenService.generateMap(mapBaseParameter, nodes);
		}

	}

	/**
	 * Adds the answering nodes to the generation algorithm.
	 */
	private void addToAvailableNodes(ActorIdentity msg) {

		if (!msg.correlationId().equals(currentLookupIdent)) {
			// not the current lookup.
			return;
		}

		if (msg.getRef() != null) {
			LOG.debug("Map generator client node identified: {}", msg.getRef());
			availableNodes.add(msg.getRef());
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
		context().system().scheduler().scheduleOnce(Duration.create(3, TimeUnit.SECONDS), getSelf(), START_MSG,
				context().dispatcher(), null);
	}

}
