using Godot;

namespace BestiaBehemothClient.Bnet.Message.Compendium
{
  /// <summary>One species the master has seen.</summary>
  [GlobalClass]
  public partial class CompendiumEntry : GodotObject
  {
    [Export] public int SpeciesId { get; set; }
    [Export] public bool Caught { get; set; }

    /// <summary>Unix time in seconds, as <c>Time.get_datetime_string_from_unix_time</c> takes it.</summary>
    [Export] public long FirstSeenUnix { get; set; }

    public static CompendiumEntry FromProto(global::Bnet.CompendiumEntry proto)
    {
      return new CompendiumEntry
      {
        SpeciesId = (int)proto.SpeciesId,
        Caught = proto.Caught,
        FirstSeenUnix = proto.FirstSeenEpochMs / 1000
      };
    }
  }
}
