package net.bestia.zoneserver.util;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

public class CurrentThreadExecutorService extends AbstractExecutorService {

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit)
			throws InterruptedException {
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
