using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.Text;
using System.Threading;
using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message
{
  /// <summary>
  /// What one message type contributed to a window.
  /// </summary>
  public readonly record struct TypeTally(string Name, long Count, long Bytes);

  /// <summary>
  /// How much the socket reports about its traffic.
  /// </summary>
  public enum NetLogMode
  {
    Off,
    Summary,
    Detail
  }

  /// <summary>
  /// Counts socket traffic per message type and reports one line per second, instead of a line per
  /// message.
  /// </summary>
  /// <remarks>
  /// The server pushes dirty components every tick to every client that can see the entity, so the
  /// message rate grows with the number of visible entities while the console's capacity does not -
  /// Godot throttles debugger output and reports the overrun as an error. Rates per message type also
  /// say more about where the traffic goes than the per-message dumps they replace.
  /// </remarks>
  public static class NetLog
  {
    /// Where a finished line goes, replaceable so the formatting can be tested without an engine.
    public static Action<string> Sink { get; set; } = line => GD.Print(line);

    /// How much the socket says. Summary is the one per second; Detail adds a line per message for
    /// whatever <see cref="SetFilter"/> lets through.
    public static NetLogMode Mode { get; set; } = NetLogMode.Summary;

    /// How often a summary lands. One second is slow enough to be quiet and fast enough to watch a
    /// spike arrive.
    public static TimeSpan FlushInterval { get; set; } = TimeSpan.FromSeconds(1);

    /// A login touches some forty message types, and a line listing all of them would recreate the
    /// problem at one hertz.
    private const int MaxTypesListed = 6;

    /// <summary>
    /// The ceiling that makes the console safe no matter how the filter is set.
    /// </summary>
    /// <remarks>
    /// Without it a filter that lets a per-tick component through puts the client straight back into
    /// the flood this class exists to stop, and the mistake would look like an engine fault rather
    /// than like a setting. Overshoot is reported rather than dropped quietly, so the filter can be
    /// seen to be wrong.
    /// </remarks>
    private const int MaxDetailLinesPerWindow = 120;

    private static readonly string[] TypeNames = BuildTypeNames();
    private static readonly long[] RxCount = new long[TypeNames.Length];
    private static readonly long[] RxBytes = new long[TypeNames.Length];
    private static readonly long[] TxCount = new long[TypeNames.Length];
    private static readonly long[] TxBytes = new long[TypeNames.Length];

    private static readonly Stopwatch Window = Stopwatch.StartNew();
    private static int _queuePeak;

    private static MessageFilter _filter = MessageFilter.Parse(null);
    private static int _detailLines;
    private static int _detailSuppressed;

    /// <summary>
    /// Counts an arrived message. Called from the socket thread.
    /// </summary>
    /// <remarks>
    /// Counted where the frame is parsed rather than where it is handled, so the figures describe
    /// arrival. That is what makes the queue depth next to them meaningful.
    /// </remarks>
    public static void CountRx(Envelope.MessageOneofCase type, int payloadBytes) =>
      Count(RxCount, RxBytes, type, payloadBytes);

    public static void CountTx(Envelope.MessageOneofCase type, int payloadBytes) =>
      Count(TxCount, TxBytes, type, payloadBytes);

    /// <summary>
    /// Offers the current receive-queue depth; the deepest seen rides the next RX line.
    /// </summary>
    /// <remarks>
    /// The one figure that separates "handling fell behind" from "the traffic was simply heavy",
    /// which the message counts alone cannot tell apart.
    /// </remarks>
    public static void NoteQueueDepth(int depth)
    {
      int peak;
      while (depth > (peak = Volatile.Read(ref _queuePeak)))
      {
        if (Interlocked.CompareExchange(ref _queuePeak, depth, peak) == peak)
        {
          return;
        }
      }
    }

    /// Which message types the detail lines cover. See <see cref="MessageFilter"/>.
    public static void SetFilter(IEnumerable<string> entries) => _filter = MessageFilter.Parse(entries);

    /// <summary>
    /// Sets <see cref="Mode"/> from the spelling used in the settings file.
    /// </summary>
    /// <remarks>
    /// An unrecognised value says so and falls back to the summary. Falling back to silence would
    /// make a typo look like a client that has nothing to report.
    /// </remarks>
    public static void SetMode(string mode)
    {
      switch (mode?.Trim().ToLowerInvariant())
      {
        case "off":
          Mode = NetLogMode.Off;
          return;
        case "summary":
          Mode = NetLogMode.Summary;
          return;
        case "detail":
          Mode = NetLogMode.Detail;
          return;
      }

      Mode = NetLogMode.Summary;
      Sink($"NET: '{mode}' is not a net_log_mode, using summary");
    }

    public static void TraceRx(Envelope envelope) => Trace("RX", envelope);

    public static void TraceTx(Envelope envelope) => Trace("TX", envelope);

    /// <summary>
    /// Flushes a window once one has elapsed. Driven from <c>BnetSocket._Process</c>, so this is the
    /// main thread.
    /// </summary>
    public static void Tick()
    {
      if (Window.Elapsed < FlushInterval)
      {
        return;
      }

      var seconds = Window.Elapsed.TotalSeconds;
      Window.Restart();

      Emit(FormatWindow("RX", seconds, Drain(RxCount, RxBytes), Interlocked.Exchange(ref _queuePeak, 0)));
      Emit(FormatWindow("TX", seconds, Drain(TxCount, TxBytes), null));

      if (_detailSuppressed > 0)
      {
        Sink($"NET: {_detailSuppressed} detail lines suppressed by the {MaxDetailLinesPerWindow}/window cap");
        _detailSuppressed = 0;
      }

      _detailLines = 0;
    }

    private static void Trace(string direction, Envelope envelope)
    {
      if (Mode != NetLogMode.Detail || !_filter.Allows(TypeName(envelope.MessageCase)))
      {
        return;
      }

      if (_detailLines >= MaxDetailLinesPerWindow)
      {
        _detailSuppressed++;
        return;
      }

      _detailLines++;
      Sink($"NET {direction} {TypeName(envelope.MessageCase)} {Describe(envelope)}");
    }

    private static string TypeName(Envelope.MessageOneofCase type)
    {
      var index = (int)type;

      return index > 0 && index < TypeNames.Length ? TypeNames[index] : "?";
    }

    /// <summary>
    /// The window's line, or null when nothing moved and there is nothing worth saying.
    /// </summary>
    /// <remarks>
    /// Invariant culture throughout: these lines get pasted into bug reports and compared against
    /// each other, so a decimal comma on one machine and a point on the next would be a nuisance.
    /// </remarks>
    public static string FormatWindow(
      string direction,
      double seconds,
      IReadOnlyList<TypeTally> tallies,
      int? peakQueue)
    {
      if (tallies.Count == 0)
      {
        return null;
      }

      long totalCount = 0;
      long totalBytes = 0;
      foreach (var tally in tallies)
      {
        totalCount += tally.Count;
        totalBytes += tally.Bytes;
      }

      var line = new StringBuilder("NET ").Append(direction).Append(' ')
        .Append(seconds.ToString("0.0", CultureInfo.InvariantCulture)).Append("s: ")
        .Append(totalCount).Append(" msgs ").Append(FormatBytes(totalBytes));

      if (peakQueue.HasValue)
      {
        line.Append(", peak queue ").Append(peakQueue.Value.ToString(CultureInfo.InvariantCulture));
      }

      var ranked = new List<TypeTally>(tallies);
      ranked.Sort((left, right) => right.Count.CompareTo(left.Count));

      var listed = Math.Min(MaxTypesListed, ranked.Count);
      line.Append(" | ");
      for (var i = 0; i < listed; i++)
      {
        if (i > 0)
        {
          line.Append(", ");
        }

        line.Append(ranked[i].Name).Append(" x").Append(ranked[i].Count)
          .Append(' ').Append(FormatBytes(ranked[i].Bytes));
      }

      if (ranked.Count > listed)
      {
        line.Append(" (+").Append(ranked.Count - listed).Append(" types)");
      }

      return line.ToString();
    }

    /// <summary>
    /// A loggable description of an envelope.
    /// </summary>
    /// <remarks>
    /// Protobuf's own <c>ToString</c> escapes every byte of a <c>bytes</c> field, so printing a chunk payload
    /// that way turns three kilobytes of terrain into some fifteen kilobytes of log - per chunk, and a login
    /// streams over a hundred of them. That is enough to stall the frame that prints it. Chunk-carrying
    /// envelopes therefore get a summary, and the authentication token gets one for an unrelated reason;
    /// everything else keeps the full dump it always had.
    /// </remarks>
    public static string Describe(Envelope envelope)
    {
      if (envelope.Authentication != null)
      {
        // A signed JWT that grants the whole account, and a log outlives the session it came from.
        return $"Authentication(token {envelope.Authentication.Token.Length} chars, " +
               $"client {envelope.Authentication.ClientVersion})";
      }

      if (envelope.ChunkData != null)
      {
        var chunk = envelope.ChunkData;
        return $"ChunkData({chunk.Pos.X},{chunk.Pos.Y},{chunk.Pos.Z}) rev {chunk.Revision}, " +
               $"{chunk.Payload.Length} B {chunk.Compression}";
      }

      if (envelope.ChunkPatch != null)
      {
        var patch = envelope.ChunkPatch;
        return $"ChunkPatch({patch.Pos.X},{patch.Pos.Y},{patch.Pos.Z}) " +
               $"rev {patch.FromRevision}->{patch.ToRevision}, {patch.Removals.Length} B";
      }

      if (envelope.ChunkManifest != null)
      {
        var manifest = envelope.ChunkManifest;
        return $"ChunkManifest(reset={manifest.Reset}, +{manifest.Added.Count}, -{manifest.Removed.Count})";
      }

      if (envelope.ChunkGroundOverlay != null)
      {
        // 128 bytes of bitmask, and protobuf's own ToString escapes every byte of it - so most of a kilobyte
        // of log per send, several times a second per column for the length of a fire.
        var overlay = envelope.ChunkGroundOverlay;
        return $"ChunkGroundOverlay({overlay.Pos.X},{overlay.Pos.Y}) {overlay.Burning.Length}B burning";
      }

      if (envelope.ChunkGroundLayers != null)
      {
        // One mask per layer the column has anything of, so the dump grows with the number of layers rather
        // than being merely large once.
        var layers = envelope.ChunkGroundLayers;
        var cells = 0;
        foreach (var layer in layers.Layers)
        {
          cells += layer.Cells.Length;
        }

        return $"ChunkGroundLayers({layers.Pos.X},{layers.Pos.Y}) " +
               $"{layers.Layers.Count} layers, {cells}B";
      }

      if (envelope.ChunkGroundStamps != null)
      {
        // The busiest of the three: footprints land several times a second per column while anything is
        // walking near the player.
        var stamps = envelope.ChunkGroundStamps;
        return $"ChunkGroundStamps({stamps.Pos.X},{stamps.Pos.Y}) " +
               $"{stamps.Stamps.Length}B {stamps.Encoding}";
      }

      if (envelope.ChunkStaticEntities != null)
      {
        // A few hundred entries per chunk and one of these behind every chunk payload, so the default dump
        // would put more log on the wire than the message carries. The same trap ChunkData already hit.
        var statics = envelope.ChunkStaticEntities;
        return $"ChunkStaticEntities({statics.Pos.X},{statics.Pos.Y},{statics.Pos.Z}) " +
               $"{statics.Entries.Count} entries";
      }

      return envelope.ToString();
    }

    private static void Emit(string line)
    {
      if (line != null && Mode != NetLogMode.Off)
      {
        Sink(line);
      }
    }

    private static void Count(long[] counts, long[] bytes, Envelope.MessageOneofCase type, int payloadBytes)
    {
      var index = (int)type;
      if (index <= 0 || index >= counts.Length)
      {
        return;
      }

      Interlocked.Increment(ref counts[index]);
      Interlocked.Add(ref bytes[index], payloadBytes);
    }

    private static List<TypeTally> Drain(long[] counts, long[] bytes)
    {
      var tallies = new List<TypeTally>();
      for (var i = 0; i < counts.Length; i++)
      {
        var count = Interlocked.Exchange(ref counts[i], 0);
        var size = Interlocked.Exchange(ref bytes[i], 0);
        if (count > 0)
        {
          tallies.Add(new TypeTally(TypeNames[i], count, size));
        }
      }

      return tallies;
    }

    private static string FormatBytes(long value)
    {
      if (value < 1024)
      {
        return value.ToString(CultureInfo.InvariantCulture) + " B";
      }

      if (value < 1024 * 1024)
      {
        return (value / 1024.0).ToString("0.0", CultureInfo.InvariantCulture) + " KB";
      }

      return (value / (1024.0 * 1024.0)).ToString("0.0", CultureInfo.InvariantCulture) + " MB";
    }

    /// <summary>
    /// Message-type names indexed by protobuf field number.
    /// </summary>
    /// <remarks>
    /// Every <c>MessageOneofCase</c> value is the field number, so a name lookup is an array index
    /// rather than the reflection an <c>enum.ToString()</c> would cost per message. The names come out
    /// in the same snake_case that zone-server's <c>socket.filter-log-messages</c> is written in.
    /// </remarks>
    private static string[] BuildTypeNames()
    {
      var fields = Envelope.Descriptor.Fields.InFieldNumberOrder();

      var highest = 0;
      foreach (var field in fields)
      {
        highest = Math.Max(highest, field.FieldNumber);
      }

      var names = new string[highest + 1];
      Array.Fill(names, "?");
      foreach (var field in fields)
      {
        names[field.FieldNumber] = field.Name;
      }

      return names;
    }
  }
}
