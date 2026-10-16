using BestiaBehemothClient.Bnet.Message.System;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// Godot-friendly wrapper for one settlement spawn point candidate a new master could choose to
  /// start life near.
  /// </summary>
  [GlobalClass]
  public partial class MasterSpawnPointCandidate : GodotObject
  {
    [Export]
    public uint Id { get; set; }

    [Export]
    public string SettlementName { get; set; } = string.Empty;

    [Export]
    public string Tier { get; set; } = string.Empty;

    [Export]
    public uint Population { get; set; }

    /// <summary>Voxel coordinates of the spawn point.</summary>
    [Export]
    public long X { get; set; }

    [Export]
    public long Y { get; set; }

    /// <summary>A translation key, e.g. <c>BIOME_TEMPERATE_FOREST</c>.</summary>
    [Export]
    public string BiomeToken { get; set; } = string.Empty;

    /// <summary>
    /// What the town's history says about it, rendered like any dialog line; see
    /// <c>Game/UI/Dialog/dialog_text.gd</c>.
    /// </summary>
    [Export]
    public Godot.Collections.Array<ConversationLine> History { get; set; } = new();

    public static MasterSpawnPointCandidate FromProto(global::Bnet.MasterSpawnPointCandidate protoCandidate)
    {
      var history = new Godot.Collections.Array<ConversationLine>();
      foreach (var line in protoCandidate.History)
      {
        history.Add(ConversationLine.FromProto(line));
      }

      return new MasterSpawnPointCandidate
      {
        Id = protoCandidate.Id,
        SettlementName = protoCandidate.SettlementName,
        Tier = protoCandidate.Tier,
        Population = protoCandidate.Population,
        X = protoCandidate.X,
        Y = protoCandidate.Y,
        BiomeToken = protoCandidate.BiomeToken,
        History = history
      };
    }
  }
}
