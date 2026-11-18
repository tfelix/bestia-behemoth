using BestiaBehemothClient.Bnet.Message.System;
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

    /// <summary>The step of the quest this belongs to.</summary>
    [Export] public int Stage { get; set; }

    /// <summary>How to word the objective; its key is empty for a delivery or a kill.</summary>
    [Export] public TextRef Text { get; set; } = new();

    /// <summary>What a decide step offers; answered with a <see cref="DecideQuestCMSG"/>.</summary>
    [Export] public Godot.Collections.Array<QuestChoiceEntry> Choices { get; set; } = [];

    /// <summary>The id the master picked; empty while it has not.</summary>
    [Export] public string Chosen { get; set; } = "";

    public static QuestObjectiveEntry FromProto(global::Bnet.QuestObjective proto)
    {
      var choices = new Godot.Collections.Array<QuestChoiceEntry>();
      foreach (var choice in proto.Choices)
      {
        choices.Add(QuestChoiceEntry.FromProto(choice));
      }

      return new QuestObjectiveEntry
      {
        KindName = EnumName.Of(proto.Kind),
        ScopeName = EnumName.Of(proto.Scope),
        ItemId = proto.ItemId,
        SpeciesId = proto.SpeciesId,
        PlaceName = proto.PlaceName,
        Required = (int)proto.Required,
        Progress = (int)proto.Progress,
        Yours = (int)proto.Yours,
        Stage = (int)proto.Stage,
        Text = proto.Text == null ? new TextRef() : TextRef.FromProto(proto.Text),
        Choices = choices,
        Chosen = proto.Chosen
      };
    }
  }
}
