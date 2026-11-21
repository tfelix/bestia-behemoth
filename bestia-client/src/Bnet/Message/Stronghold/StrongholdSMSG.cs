using Godot;

namespace BestiaBehemothClient.Bnet.Message.Stronghold
{
  /// <summary>
  /// The Stronghold window at the guild's Empyrite. Each track is a dictionary with <c>track</c> (the proto's
  /// StrongholdTrack as int), <c>level</c>, <c>steps</c> and <c>next_step_coins</c> (0 at the top level).
  /// </summary>
  [GlobalClass]
  public partial class StrongholdSMSG : ISMSG
  {
    [Export] public ulong EmpyriteEntityId { get; set; }
    [Export] public string SeedName { get; set; } = "";
    [Export] public string GuildName { get; set; } = "";
    [Export] public int Hp { get; set; }
    [Export] public int MaxHp { get; set; }
    [Export] public Godot.Collections.Array<Godot.Collections.Dictionary> Tracks { get; set; } = [];
    [Export] public bool HasPermanentDevelopment { get; set; }
    [Export] public int MaxLevel { get; set; }
    [Export] public int StepsPerLevel { get; set; }
    [Export] public int HpPerDefense { get; set; }
    [Export] public int DefPerDefense { get; set; }
    [Export] public bool HasStewardContract { get; set; }
    [Export] public bool HasSteward { get; set; }
    [Export] public long StewardCoins { get; set; }
    [Export] public bool HasWatcherResearch { get; set; }
    [Export] public bool MaySetWatchers { get; set; }
    [Export] public int WatcherPosts { get; set; }
    [Export] public int WatcherLevel { get; set; }
    [Export] public int MeleeWatchers { get; set; }
    [Export] public int ArcherWatchers { get; set; }

    /// <summary>Soonest back first: dictionaries with <c>kind</c> (the proto's WatcherKind) and <c>seconds_left</c>.</summary>
    [Export] public Godot.Collections.Array<Godot.Collections.Dictionary> FallenWatchers { get; set; } = [];
    [Export] public long BringBackCoins { get; set; }

    public static StrongholdSMSG FromProto(global::Bnet.StrongholdSMSG proto)
    {
      var tracks = new Godot.Collections.Array<Godot.Collections.Dictionary>();
      foreach (var track in proto.Tracks)
      {
        tracks.Add(new Godot.Collections.Dictionary
        {
          ["track"] = (int)track.Track,
          ["level"] = (int)track.Level,
          ["steps"] = (int)track.Steps,
          ["next_step_coins"] = (long)track.NextStepCoins
        });
      }

      var fallen = new Godot.Collections.Array<Godot.Collections.Dictionary>();
      foreach (var watcher in proto.FallenWatchers)
      {
        fallen.Add(new Godot.Collections.Dictionary
        {
          ["kind"] = (int)watcher.Kind,
          ["seconds_left"] = (int)watcher.SecondsLeft
        });
      }

      return new StrongholdSMSG
      {
        EmpyriteEntityId = proto.EmpyriteEntityId,
        SeedName = proto.SeedName,
        GuildName = proto.GuildName,
        Hp = (int)proto.Hp,
        MaxHp = (int)proto.MaxHp,
        Tracks = tracks,
        HasPermanentDevelopment = proto.HasPermanentDevelopment,
        MaxLevel = (int)proto.MaxLevel,
        StepsPerLevel = (int)proto.StepsPerLevel,
        HpPerDefense = (int)proto.HpPerDefense,
        DefPerDefense = (int)proto.DefPerDefense,
        HasStewardContract = proto.HasStewardContract,
        HasSteward = proto.HasSteward,
        StewardCoins = (long)proto.StewardCoins,
        HasWatcherResearch = proto.HasWatcherResearch,
        MaySetWatchers = proto.MaySetWatchers,
        WatcherPosts = (int)proto.WatcherPosts,
        WatcherLevel = (int)proto.WatcherLevel,
        MeleeWatchers = (int)proto.MeleeWatchers,
        ArcherWatchers = (int)proto.ArcherWatchers,
        FallenWatchers = fallen,
        BringBackCoins = (long)proto.BringBackCoins
      };
    }
  }
}
