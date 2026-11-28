using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// Something one of the player's bestias did, as its diary tells it. Empty strings mean "none".
  /// </summary>
  [GlobalClass]
  public partial class BestiaNewsSMSG : ISMSG
  {
    [Export] public ulong PlayerBestiaId { get; set; }

    /// <summary>NEW_ORDERS, HAULED, WORK_PAUSED, QUEUE_FINISHED, ATTACKED, RETREATED, DIED, LOST, OUT_OF_FOOD,
    /// LOW_FOOD, EVENT_SENSED, FIELD_EMPTY or FALLBACK_STARTED.</summary>
    [Export] public string Kind { get; set; } = "";

    /// <summary>Item identifier to amount, for a haul.</summary>
    [Export] public Godot.Collections.Dictionary<string, int> Items { get; set; } = new();

    /// <summary>A station's kind or, for a haul, the chest's name.</summary>
    [Export] public string Place { get; set; } = "";

    [Export] public string Reason { get; set; } = "";

    [Export] public long AtEpochMs { get; set; }

    public static BestiaNewsSMSG FromProto(global::Bnet.BestiaNewsSMSG proto)
    {
      var news = new BestiaNewsSMSG
      {
        PlayerBestiaId = proto.PlayerBestiaId,
        Kind = proto.Kind,
        Place = proto.Place,
        Reason = proto.Reason,
        AtEpochMs = proto.AtEpochMs
      };
      foreach (var (item, amount) in proto.Items)
      {
        news.Items[item] = (int)amount;
      }

      return news;
    }
  }
}
