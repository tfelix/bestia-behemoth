using Godot;

namespace BestiaBehemothClient.Bnet.Message.Quest
{
  /// <summary>
  /// One thing a quest asks for. <see cref="Progress"/> is the shared total for a shared objective;
  /// <see cref="Yours"/> is what the played master did.
  /// </summary>
  [GlobalClass]
  public partial class QuestObjectiveEntry : GodotObject
  {
    [Export] public string KindName { get; set; } = "";
    [Export] public string ScopeName { get; set; } = "";
    [Export] public long ItemId { get; set; }
    [Export] public long SpeciesId { get; set; }

    /// <summary>The place a kill must happen in; empty when anywhere counts.</summary>
    [Export] public string PlaceName { get; set; } = "";
    [Export] public int Required { get; set; }
    [Export] public int Progress { get; set; }
    [Export] public int Yours { get; set; }

    public static QuestObjectiveEntry FromProto(global::Bnet.QuestObjective proto)
    {
      return new QuestObjectiveEntry
      {
        KindName = EnumName.Of(proto.Kind),
        ScopeName = EnumName.Of(proto.Scope),
        ItemId = proto.ItemId,
        SpeciesId = proto.SpeciesId,
        PlaceName = proto.PlaceName,
        Required = (int)proto.Required,
        Progress = (int)proto.Progress,
        Yours = (int)proto.Yours
      };
    }
  }
}
