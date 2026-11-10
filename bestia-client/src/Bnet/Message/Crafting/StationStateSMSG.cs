using Godot;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>A station's queue and the ingredients it holds. See <c>station_state_smsg.proto</c>.</summary>
  [GlobalClass]
  public partial class StationStateSMSG : ISMSG
  {
    [Export] public ulong StructureId { get; set; }

    [Export] public Godot.Collections.Array<StationQueueEntry> Entries { get; set; } = [];

    /// <summary>Item id -> amount paid in and not used yet.</summary>
    [Export] public Godot.Collections.Dictionary<uint, uint> Held { get; set; } = [];

    /// <summary>
    /// "none", "worker_away", "worker_offline", "bag_full", "station_out", "cannot_craft" or "no_free_crafter", so
    /// GDScript can match without the enum.
    /// </summary>
    [Export] public string PauseName { get; set; } = "none";

    /// <summary>The bestia that works the queue, or 0 when the owner does.</summary>
    [Export] public ulong WorkerEntityId { get; set; }

    /// <summary>Any free crafter may take the station; <see cref="WorkerEntityId"/> is the one that did, or 0.</summary>
    [Export] public bool AnyFreeCrafter { get; set; }

    public static StationStateSMSG FromProto(global::Bnet.StationStateSMSG proto)
    {
      var message = new StationStateSMSG
      {
        StructureId = proto.StructureId,
        WorkerEntityId = proto.WorkerEntityId,
        AnyFreeCrafter = proto.AnyFreeCrafter,
        PauseName = proto.Pause switch
        {
          global::Bnet.StationPause.WorkerAway => "worker_away",
          global::Bnet.StationPause.WorkerOffline => "worker_offline",
          global::Bnet.StationPause.BagFull => "bag_full",
          global::Bnet.StationPause.StationOut => "station_out",
          global::Bnet.StationPause.CannotCraft => "cannot_craft",
          global::Bnet.StationPause.NoFreeCrafter => "no_free_crafter",
          _ => "none"
        }
      };

      foreach (var entry in proto.Entries)
      {
        message.Entries.Add(new StationQueueEntry
        {
          RecipeId = entry.RecipeId,
          Amount = entry.Amount,
          Done = entry.Done,
          ProgressMillis = entry.ProgressMillis,
          CraftMillis = entry.CraftMillis
        });
      }

      foreach (var pile in proto.Held)
      {
        message.Held[pile.ItemId] = pile.Amount;
      }

      return message;
    }

    public override string ToString() => $"StationStateSMSG(Structure={StructureId}, Entries={Entries.Count}, Pause={PauseName})";
  }
}
