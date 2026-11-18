using BestiaBehemothClient.Bnet.Message.System;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Quest
{
  /// <summary>
  /// One quest in the played master's log, complete on its own: quests are made at runtime, so there is no
  /// definition to look up. <see cref="RoleName"/> is <c>accepted</c> or <c>posted</c>.
  /// </summary>
  [GlobalClass]
  public partial class QuestEntry : GodotObject
  {
    [Export] public ulong QuestId { get; set; }
    [Export] public string OriginName { get; set; } = "";
    [Export] public string StatusName { get; set; } = "";
    [Export] public string RoleName { get; set; } = "";
    [Export] public TextRef Title { get; set; } = new();
    [Export] public int SuggestedLevel { get; set; }
    [Export] public long DeadlineEpochMs { get; set; }
    [Export] public string PosterName { get; set; } = "";
    [Export] public Godot.Collections.Array<QuestObjectiveEntry> Objectives { get; set; } = [];
    [Export] public Godot.Collections.Array<QuestRewardEntry> Rewards { get; set; } = [];

    public static QuestEntry FromProto(global::Bnet.Quest proto)
    {
      var quest = new QuestEntry
      {
        QuestId = proto.QuestId,
        OriginName = EnumName.Of(proto.Origin),
        StatusName = EnumName.Of(proto.Status),
        RoleName = EnumName.Of(proto.Role),
        Title = TextRef.FromProto(proto.Title),
        SuggestedLevel = (int)proto.SuggestedLevel,
        DeadlineEpochMs = proto.DeadlineEpochMs,
        PosterName = proto.PosterName
      };
      foreach (var objective in proto.Objectives)
      {
        quest.Objectives.Add(QuestObjectiveEntry.FromProto(objective));
      }
      foreach (var reward in proto.Rewards)
      {
        quest.Rewards.Add(QuestRewardEntry.FromProto(reward));
      }

      return quest;
    }
  }
}
