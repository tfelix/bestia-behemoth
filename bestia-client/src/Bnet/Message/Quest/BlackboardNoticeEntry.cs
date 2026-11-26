using Godot;

namespace BestiaBehemothClient.Bnet.Message.Quest
{
  /// <summary>A running world event a board posts. Nobody signs it: whoever helps shares its loot and honor.</summary>
  [GlobalClass]
  public partial class BlackboardNoticeEntry : GodotObject
  {
    /// <summary>The world_events.yml id, named by the WORLD_EVENT_ keys.</summary>
    [Export] public string EventId { get; set; } = "";
    [Export] public string PlaceName { get; set; } = "";
    [Export] public long X { get; set; }
    [Export] public long Y { get; set; }
    /// <summary>1 stirring, 2 gathering, 3 about to break.</summary>
    [Export] public int Stage { get; set; }

    public static BlackboardNoticeEntry FromProto(global::Bnet.BlackboardNotice proto)
    {
      return new BlackboardNoticeEntry
      {
        EventId = proto.EventId,
        PlaceName = proto.PlaceName,
        X = proto.X,
        Y = proto.Y,
        Stage = (int)proto.Stage
      };
    }
  }
}
