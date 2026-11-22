using BestiaBehemothClient.Bnet.Message.Guild;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Stronghold
{
  /// <summary>
  /// The guild window's Stronghold tab. Each card is a dictionary with <c>seed_rank</c>, <c>name</c>, <c>is_site</c>,
  /// <c>hp</c>, <c>max_hp</c>, <c>defense</c>, <c>storage</c>, <c>economy</c>, <c>has_steward</c>, <c>watchers</c>,
  /// <c>watcher_posts</c>, <c>max_drops</c>, <c>next_drop_seconds</c> and <c>drops</c>: oldest first, each with
  /// <c>made_at_ms</c>, <c>coins</c> and <c>goods</c> (dictionaries of <c>item_id</c> and <c>amount</c>).
  /// </summary>
  [GlobalClass]
  public partial class StrongholdTabSMSG : GuildSMSG
  {
    [Export] public Godot.Collections.Array<Godot.Collections.Dictionary> Strongholds { get; set; } = [];
    [Export] public bool IsLeader { get; set; }
    [Export] public bool HasGuildStorage { get; set; }
    [Export] public int StorageUsed { get; set; }
    [Export] public int StorageSlots { get; set; }
    [Export] public bool StorageSealed { get; set; }
    [Export] public long SendAwayCoins { get; set; }

    public static StrongholdTabSMSG FromProto(global::Bnet.StrongholdTabSMSG proto)
    {
      var cards = new Godot.Collections.Array<Godot.Collections.Dictionary>();
      foreach (var card in proto.Strongholds)
      {
        var drops = new Godot.Collections.Array();
        foreach (var drop in card.Drops)
        {
          var goods = new Godot.Collections.Array();
          foreach (var good in drop.Goods)
          {
            goods.Add(new Godot.Collections.Dictionary { ["item_id"] = (long)good.ItemId, ["amount"] = (int)good.Amount });
          }
          drops.Add(new Godot.Collections.Dictionary
          {
            ["made_at_ms"] = drop.MadeAtEpochMillis,
            ["coins"] = (long)drop.Coins,
            ["goods"] = goods
          });
        }

        cards.Add(new Godot.Collections.Dictionary
        {
          ["seed_rank"] = (int)card.SeedRank,
          ["name"] = card.Name,
          ["is_site"] = card.IsSite,
          ["hp"] = (int)card.Hp,
          ["max_hp"] = (int)card.MaxHp,
          ["defense"] = (int)card.Defense,
          ["storage"] = (int)card.Storage,
          ["economy"] = (int)card.Economy,
          ["has_steward"] = card.HasSteward,
          ["watchers"] = (int)card.Watchers,
          ["watcher_posts"] = (int)card.WatcherPosts,
          ["max_drops"] = (int)card.MaxDrops,
          ["next_drop_seconds"] = (int)card.NextDropSeconds,
          ["drops"] = drops
        });
      }

      return new StrongholdTabSMSG
      {
        Strongholds = cards,
        IsLeader = proto.IsLeader,
        HasGuildStorage = proto.HasGuildStorage,
        StorageUsed = (int)proto.StorageUsed,
        StorageSlots = (int)proto.StorageSlots,
        StorageSealed = proto.StorageSealed,
        SendAwayCoins = (long)proto.SendAwayCoins
      };
    }
  }
}
