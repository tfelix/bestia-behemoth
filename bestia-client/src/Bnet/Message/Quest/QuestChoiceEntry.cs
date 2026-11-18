using BestiaBehemothClient.Bnet.Message.System;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Quest
{
  /// <summary>One thing a decide step lets the master pick.</summary>
  [GlobalClass]
  public partial class QuestChoiceEntry : GodotObject
  {
    [Export] public string Id { get; set; } = "";
    [Export] public TextRef Text { get; set; } = new();

    public static QuestChoiceEntry FromProto(global::Bnet.QuestChoice proto)
    {
      return new QuestChoiceEntry { Id = proto.Id, Text = TextRef.FromProto(proto.Text) };
    }
  }
}
