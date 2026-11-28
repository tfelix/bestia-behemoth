using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// One bestia's part of the away report. Empty strings mean "none".
  /// </summary>
  [GlobalClass]
  public partial class BestiaAwayReport : GodotObject
  {
    [Export] public ulong PlayerBestiaId { get; set; }

    /// <summary>Empty when it has no name of its own.</summary>
    [Export] public string Name { get; set; } = "";

    [Export] public string Species { get; set; } = "";

    /// <summary>Item identifier to the amount it hauled.</summary>
    [Export] public Godot.Collections.Dictionary<string, int> Brought { get; set; } = new();

    /// <summary>The named chests the goods went to.</summary>
    [Export] public Godot.Collections.Array<string> BroughtTo { get; set; } = [];

    /// <summary>Station kind to the queues it finished there.</summary>
    [Export] public Godot.Collections.Dictionary<string, int> MadeAt { get; set; } = new();

    /// <summary>State (WORKING, IDLE, RESTING, PAUSED, FIGHTING, SHELTERING, TRAVELLING) to seconds.</summary>
    [Export] public Godot.Collections.Dictionary<string, long> TimeIn { get; set; } = new();

    /// <summary>Oldest first.</summary>
    [Export] public Godot.Collections.Array<BestiaIncident> Incidents { get; set; } = [];

    /// <summary>An activity kind as in <see cref="BestiaActivityInfo.Kind"/>, NOT_IN_WORLD or DEAD.</summary>
    [Export] public string Now { get; set; } = "";

    [Export] public string NowAt { get; set; } = "";

    /// <summary>CHEST_FULL, NO_ORE, STATION_OUT, CANNOT_CRAFT, OUT_OF_FOOD or DIED.</summary>
    [Export] public string Blocker { get; set; } = "";

    /// <summary>EMPTY_CHEST, BUILD_SHAFT, FEED_FIRE, ADD_INGREDIENTS, BRING_FOOD or REVIVE.</summary>
    [Export] public string Hint { get; set; } = "";

    public static BestiaAwayReport FromProto(global::Bnet.BestiaAwayReport proto)
    {
      var report = new BestiaAwayReport
      {
        PlayerBestiaId = proto.PlayerBestiaId,
        Name = proto.Name,
        Species = proto.Species,
        Now = proto.Now,
        NowAt = proto.NowAt,
        Blocker = proto.Blocker,
        Hint = proto.Hint
      };
      foreach (var (item, amount) in proto.Brought)
      {
        report.Brought[item] = (int)amount;
      }

      foreach (var chest in proto.BroughtTo)
      {
        report.BroughtTo.Add(chest);
      }

      foreach (var (station, queues) in proto.MadeAt)
      {
        report.MadeAt[station] = (int)queues;
      }

      foreach (var (state, seconds) in proto.TimeIn)
      {
        report.TimeIn[state] = (long)seconds;
      }

      foreach (var incident in proto.Incidents)
      {
        report.Incidents.Add(BestiaIncident.FromProto(incident));
      }

      return report;
    }
  }
}
