package net.bestia.zoneserver.testutil;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Executes all jobs in the current thread. Ideal for debugging because of predictable execution order. Should be used
 * in tests.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class CurrentThreadExecutorService extends AbstractExecutorService {

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return false;
	}

	@Override
	public boolean isShutdown() {
		return false;
	}

	@Override
	public boolean isTerminated() {
		return false;
	}

	@Override
	public void shutdown() {

	}

	@Override
	public List<Runnable> shutdownNow() {
		return null;
	}

	@Override
	public void execute(Runnable command) {
		command.run();
	}
}
