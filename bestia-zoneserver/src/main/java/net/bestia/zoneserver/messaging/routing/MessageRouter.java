package net.bestia.zoneserver.messaging.routing;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.Message;
import net.bestia.zoneserver.messaging.MessageHandler;

/**
 * <p>
 * The new central way of interprocess message routing. The messages which are
 * fed into the router will be checked againt the filter. Each filter who wants
 * to process a certain message will get the message delivered.
 * </p>
 * <p>
 * The router is threadsafe.
 * </p>
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MessageRouter implements MessageHandler {

	private static final Logger LOG = LogManager.getLogger(MessageRouter.class);

	private class FilterTuple {
		public final MessageFilter filter;
		public final MessageHandler processor;

		public FilterTuple(MessageFilter filter, MessageHandler processor) {

			if (filter == null) {
				throw new IllegalArgumentException("MessageFilter can not be null.");
			}
			if (processor == null) {
				throw new IllegalArgumentException("MessageProcessor can not be null.");
			}

			this.processor = processor;
			this.filter = filter;
		}
		
		@Override
		public String toString() {
			return String.format("[%s, %s]", filter.toString(), processor.toString());
		}
	}

	private final List<FilterTuple> filterList = new ArrayList<>();

	/**
	 * Registers a filter to subscribe to incoming messages.
	 * 
	 * @param filter
	 *            The filter to check the incoming messages.
	 * @param processor
	 *            The processor who will process the matching messages.
	 */
	public synchronized void registerFilter(MessageFilter filter, MessageHandler processor) {
		final FilterTuple tuple = new FilterTuple(filter, processor);
		filterList.add(tuple);
	}

	@Override
	public void handleMessage(Message msg) {
		boolean wasProcessed = false;
		for (FilterTuple tuple : filterList) {
			if (tuple.filter.handlesMessage(msg)) {
				tuple.processor.handleMessage(msg);
				wasProcessed = true;
			}
		}
		if (!wasProcessed) {
			LOG.warn("Message had no rule to be processed: {}", msg);
		}
	}

}
