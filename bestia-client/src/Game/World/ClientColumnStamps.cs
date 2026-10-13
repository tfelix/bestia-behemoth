using System;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>What a snapshot did to the prints this client had laid itself. Counted for the drift log.</summary>
  public readonly struct StampMerge
  {
    public StampMerge(int prior, int localDropped, int snapshotNew)
    {
      Prior = prior;
      LocalDropped = localDropped;
      SnapshotNew = snapshotNew;
    }

    /// <summary>Prints held before the snapshot.</summary>
    public int Prior { get; }

    /// <summary>Prints only this client had, and old enough that the server's silence about them counts.</summary>
    public int LocalDropped { get; }

    /// <summary>Prints the server listed that this client had not laid.</summary>
    public int SnapshotNew { get; }
  }

  /// <summary>
  /// The footprints of one chunk column: laid locally as walkers cross it, replaced by whatever the server lists.
  /// </summary>
  /// <remarks>
  /// A ring of at most <c>capacity</c> prints, oldest first so the newest draws on top. Ages are real
  /// milliseconds on the caller's clock; <see cref="ApplySnapshot"/> turns the server's strength byte back into
  /// an age. Godot-free so the merge can be tested without an engine.
  /// </remarks>
  public sealed class ClientColumnStamps
  {
    /// <summary>
    /// How long a locally laid print is trusted without the server confirming it.
    /// </summary>
    /// <remarks>
    /// Covers integrator-against-tick jitter: a snapshot arrives about one latency after the steps it lists, and
    /// the client lays the print for a step about one latency after the server took it, so the two sets differ
    /// only by a print or two near the edge.
    /// </remarks>
    public const long LocalGraceMillis = 1500;

    /// <summary>Strength steps a print is drawn at, as the server quantises them.</summary>
    public const int Levels = 16;

    private struct Print
    {
      internal int Cell;
      internal int Kind;
      internal int Octant;
      internal int Seed;
      internal long LaidAtMillis;

      /// <summary>The level last handed out, so <see cref="Age"/> can tell a visible change from a quiet tick.</summary>
      internal int ShownLevel;

      internal bool SameAs(in Print other) =>
        Cell == other.Cell && Octant == other.Octant && Seed == other.Seed;
    }

    private readonly long _lifetimeMillis;
    private readonly int _capacity;
    private Print[] _prints;
    private int _count;

    public ClientColumnStamps(int capacity, long lifetimeMillis)
    {
      if (capacity <= 0 || lifetimeMillis <= 0)
      {
        throw new ArgumentException($"a print ring needs a positive capacity and lifetime, got {capacity} and {lifetimeMillis} ms");
      }

      _capacity = capacity;
      _lifetimeMillis = lifetimeMillis;
      _prints = new Print[Math.Min(capacity, 64)];
    }

    public int Count => _count;

    public bool IsEmpty => _count == 0;

    /// <summary>
    /// Lays one print, unless the same one was laid a moment ago.
    /// </summary>
    /// <remarks>
    /// The same cell, heading and seed inside the grace window is one footfall reported twice, which a path
    /// replaced mid-step can do.
    /// </remarks>
    /// <returns>whether a print was laid</returns>
    public bool Add(int cell, int octant, int seed, long nowMillis)
    {
      var print = new Print
      {
        Cell = cell, Kind = GroundStampCells.Footprint, Octant = octant, Seed = seed, LaidAtMillis = nowMillis,
        ShownLevel = Levels
      };

      for (var i = 0; i < _count; i++)
      {
        if (_prints[i].SameAs(print) && nowMillis - _prints[i].LaidAtMillis <= LocalGraceMillis)
        {
          return false;
        }
      }

      Append(print);

      return true;
    }

    /// <summary>
    /// Replaces the ring with what the server lists, keeping only the local prints it cannot yet have seen.
    /// </summary>
    /// <remarks>
    /// <b>The snapshot wins.</b> A local print is dropped unless the snapshot lists it (matched one to one by
    /// cell, heading and seed, newest first) or it is young enough to be in flight. That is what heals a
    /// phantom - a print laid for a step the server never took - within one resync.
    /// </remarks>
    public StampMerge ApplySnapshot(GroundStamp[] snapshot, long nowMillis)
    {
      var prior = _count;

      var first = Math.Max(0, snapshot.Length - _capacity);
      var incoming = new Print[snapshot.Length - first];
      for (var i = 0; i < incoming.Length; i++)
      {
        incoming[i] = FromWire(snapshot[first + i], nowMillis);
      }

      var claimed = new bool[incoming.Length];
      var survivors = new Print[_count];
      var survived = 0;
      var dropped = 0;

      for (var i = _count - 1; i >= 0; i--)
      {
        if (Claim(incoming, claimed, _prints[i]))
        {
          continue;
        }

        if (nowMillis - _prints[i].LaidAtMillis <= LocalGraceMillis)
        {
          survivors[survived++] = _prints[i];
        }
        else
        {
          dropped++;
        }
      }

      var fresh = 0;
      foreach (var taken in claimed)
      {
        if (!taken)
        {
          fresh++;
        }
      }

      _count = 0;
      foreach (var print in incoming)
      {
        Append(print);
      }

      // Survivors were collected newest first, and are young enough to belong after everything the server listed.
      for (var i = survived - 1; i >= 0; i--)
      {
        Append(survivors[i]);
      }

      return new StampMerge(prior, dropped, fresh);
    }

    /// <summary>
    /// Drops what has expired and re-reads each print's level.
    /// </summary>
    /// <returns>whether anything went or a print's drawn level moved, which is when the field needs redrawing</returns>
    public bool Age(long nowMillis)
    {
      var changed = false;
      var kept = 0;

      for (var i = 0; i < _count; i++)
      {
        var print = _prints[i];
        var age = nowMillis - print.LaidAtMillis;

        if (age >= _lifetimeMillis)
        {
          changed = true;
          continue;
        }

        var level = LevelAt(age);
        if (level != print.ShownLevel)
        {
          print.ShownLevel = level;
          changed = true;
        }

        _prints[kept++] = print;
      }

      _count = kept;

      return changed;
    }

    /// <summary>Whether a print has life left. One that has not is dropped by the next <see cref="Age"/>.</summary>
    public bool IsLive(int index, long nowMillis) => nowMillis - _prints[index].LaidAtMillis < _lifetimeMillis;

    /// <summary>One print as it should be drawn at <paramref name="nowMillis"/>. Oldest is index 0.</summary>
    public GroundStamp StampAt(int index, long nowMillis)
    {
      var print = _prints[index];

      return new GroundStamp(
        print.Cell, print.Kind, print.Octant, print.Seed, StrengthOf(LevelAt(nowMillis - print.LaidAtMillis)));
    }

    /// <summary>The strength byte a level is drawn at, and the server sends: 15, 31, ... 255.</summary>
    public static byte StrengthOf(int level) => (byte)(level * 255 / Levels);

    /// <summary>The level a print of this age is drawn at. Never below 1: a print still here is still visible.</summary>
    public int LevelAt(long ageMillis)
    {
      var remaining = Math.Clamp(_lifetimeMillis - ageMillis, 0, _lifetimeMillis);

      return (int)Math.Clamp(remaining * Levels / _lifetimeMillis, 1, Levels);
    }

    private Print FromWire(GroundStamp stamp, long nowMillis)
    {
      var level = Math.Clamp((stamp.Strength + 1) >> 4, 1, Levels);
      var remaining = _lifetimeMillis * level / Levels;

      return new Print
      {
        Cell = stamp.CellIndex, Kind = stamp.Kind, Octant = stamp.Octant, Seed = stamp.Seed,
        LaidAtMillis = nowMillis - (_lifetimeMillis - remaining), ShownLevel = level
      };
    }

    /// <summary>Marks the newest unclaimed snapshot print that is the same print as <paramref name="local"/>.</summary>
    private static bool Claim(Print[] incoming, bool[] claimed, in Print local)
    {
      for (var i = incoming.Length - 1; i >= 0; i--)
      {
        if (!claimed[i] && incoming[i].SameAs(local))
        {
          claimed[i] = true;

          return true;
        }
      }

      return false;
    }

    private void Append(in Print print)
    {
      if (_count == _capacity)
      {
        Array.Copy(_prints, 1, _prints, 0, _count - 1);
        _count--;
      }

      if (_count == _prints.Length)
      {
        Array.Resize(ref _prints, Math.Min(_capacity, _prints.Length * 2));
      }

      _prints[_count++] = print;
    }
  }
}
