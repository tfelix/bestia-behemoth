using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// One thing that happened to a bestia, with the kind, place and reason of its diary line.
  /// </summary>
  [GlobalClass]
  public partial class BestiaIncident : GodotObject
  {
    [Export] public string Kind { get; set; } = "";

    [Export] public long AtEpochMs { get; set; }

    [Export] public string Reason { get; set; } = "";

    [Export] public string Place { get; set; } = "";

    public static BestiaIncident FromProto(global::Bnet.BestiaIncident proto)
    {
      return new BestiaIncident
      {
        Kind = proto.Kind,
        AtEpochMs = proto.AtEpochMs,
        Reason = proto.Reason,
        Place = proto.Place
      };
    }
  }
}
