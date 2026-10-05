package net.bestia.zone.ecs.core

import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinWorkerThread

/** A thread of a parallel wave; it may touch the world while the tick thread waits for its wave. */
class WaveWorker(pool: ForkJoinPool) : ForkJoinWorkerThread(pool)
