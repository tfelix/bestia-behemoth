using System;
using System.Threading;
using Godot;

namespace BestiaBehemothClient.Dev
{
  /// <summary>
  /// The .NET runtime's own counters, for the render debug readout.
  /// </summary>
  /// <remarks>
  /// GDScript cannot reach <see cref="GC"/> or <see cref="ThreadPool"/>, and nothing in Godot's monitors covers
  /// them - yet a main thread suspended for a garbage collection, or parked on a lock, looks from the outside like
  /// a frame that took long with no script and no draw to show for it. Every value here is a running total or a
  /// level; the readout turns the totals into rates over its own window.
  /// </remarks>
  [GlobalClass]
  public partial class ManagedStats : RefCounted
  {
    /// <summary>Bytes allocated on the managed heap since the process started, by every thread.</summary>
    public long AllocatedBytes() => GC.GetTotalAllocatedBytes(false);

    /// <summary>Collections of <paramref name="generation"/> (0, 1 or 2) since the process started.</summary>
    public int Collections(int generation) => GC.CollectionCount(generation);

    /// <summary>Milliseconds the runtime has had every managed thread paused for collections, in total.</summary>
    public double PausedMilliseconds() => GC.GetTotalPauseDuration().TotalMilliseconds;

    /// <summary>Bytes the managed heap holds now, live or not yet collected.</summary>
    public long HeapBytes() => GC.GetTotalMemory(false);

    /// <summary>Times a thread has had to wait for a <c>lock</c> another thread held, in total.</summary>
    public long LockContentions() => Monitor.LockContentionCount;

    /// <summary>Work items queued on the thread pool and not yet started.</summary>
    public long PoolQueue() => ThreadPool.PendingWorkItemCount;

    /// <summary>Threads the thread pool has now.</summary>
    public int PoolThreads() => ThreadPool.ThreadCount;
  }
}
