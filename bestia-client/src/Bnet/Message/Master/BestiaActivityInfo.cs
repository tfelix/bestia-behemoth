using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// What an owned bestia is doing, for its status line. Empty strings and a 0 <see cref="Total"/> mean "none".
  /// </summary>
  [GlobalClass]
  public partial class BestiaActivityInfo : GodotObject
  {
    /// <summary>CRAFTING, MINING, GATHERING, HAULING, FETCHING_FOOD, EATING, HEALING, FIGHTING, RETREATING,
    /// RESTING_AT_HOME, SLEEPING, GUARDING or IDLE.</summary>
    [Export] public string Kind { get; set; } = "";

    /// <summary>The item identifier it makes at a station.</summary>
    [Export] public string Item { get; set; } = "";

    [Export] public int Done { get; set; }

    [Export] public int Total { get; set; }

    /// <summary>The station's kind, for example WORKBENCH.</summary>
    [Export] public string Station { get; set; } = "";

    /// <summary>Why the station waits, for example STATION_OUT.</summary>
    [Export] public string Pause { get; set; } = "";

    public static BestiaActivityInfo FromProto(global::Bnet.BestiaActivity proto)
    {
      return new BestiaActivityInfo
      {
        Kind = proto.Kind,
        Item = proto.Item,
        Done = (int)proto.Done,
        Total = (int)proto.Total,
        Station = proto.Station,
        Pause = proto.Pause
      };
    }
  }
}
