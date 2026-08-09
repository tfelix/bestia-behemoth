using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// A fresh anchor for the world calendar, sent when the server's clock has jumped.
  /// </summary>
  /// <remarks>
  /// The clock is normally anchored once, by <see cref="WorldInfoSMSG"/> at login, and run forward locally -
  /// so nothing arrives per tick and nothing has to be interpolated. That covers a clock that only advances.
  /// This covers the one case it cannot: a GM moving the server's clock with <c>/date</c>, after which the
  /// local extrapolation is simply wrong and no amount of running it forward will recover.
  ///
  /// <para>
  /// A message of its own rather than a second <c>WorldInfoSMSG</c>, which carries these same two fields:
  /// that one also states the world's identity, and <c>ChunkStreamManager</c> reads it as "a new world, or a
  /// reconnect" and drops every chunk held. Setting a clock with it would re-stream the terrain.
  /// </para>
  ///
  /// <para>
  /// A <see cref="MapSMSG"/> so ConnectionManager's GDScript handler ignores it rather than reporting it as
  /// unidentified - <c>WorldClock</c> subscribes to the socket itself, the way ChunkStreamManager does.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class WorldTimeSMSG : MapSMSG
  {
    /// <summary>Bestia-seconds elapsed since the world began, as of the moment the server built this.</summary>
    [Export] public double WorldAgeBestiaSeconds { get; set; }

    /// <summary>
    /// How many times faster Bestia time runs than real time.
    /// </summary>
    /// <remarks>
    /// Repeated from <see cref="WorldInfoSMSG"/> so this is a complete anchor. It is a server setting and so
    /// can differ from what the connection was first told; re-anchoring the reading while leaving the client
    /// advancing it at a stale rate would drift rather than fail.
    /// </remarks>
    [Export] public double TimeSpeedFactor { get; set; }

    public static WorldTimeSMSG FromProto(global::Bnet.WorldTimeSMSG proto)
    {
      return new WorldTimeSMSG
      {
        WorldAgeBestiaSeconds = proto.WorldAgeBestiaSeconds,
        TimeSpeedFactor = proto.TimeSpeedFactor
      };
    }

    public override string ToString() =>
      $"world time {WorldAgeBestiaSeconds:F0} bestia-seconds @{TimeSpeedFactor:F1}x";
  }
}
