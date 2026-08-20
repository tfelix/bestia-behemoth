using System.Collections.Generic;
using BestiaBehemothClient.Game.World;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// What this client is entitled to hold, as positions and the revision each is at.
  /// </summary>
  /// <remarks>
  /// The cheap half of the protocol, and the reason the expensive half is rarely needed: about a hundred and
  /// twenty refs is roughly one and a half kilobytes, against some three hundred and seventy-five kilobytes
  /// of payload for the same chunks. Anything already held at the listed revision is not requested at all.
  ///
  /// <para>
  /// It is also the server's authorisation set. A request for a position that was never offered is dropped,
  /// so there is no point asking for anything not listed here.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class ChunkManifestSMSG : MapSMSG
  {
    public readonly struct Ref
    {
      public ChunkKey Key { get; }
      public uint Revision { get; }

      public Ref(ChunkKey key, uint revision)
      {
        Key = key;
        Revision = revision;
      }
    }

    /// <summary>
    /// Replace the whole held set rather than amending it. Anything held and not in <see cref="Added"/> is no
    /// longer subscribed. Set on the first manifest of a session only - a teleport sends an amendment, which
    /// withdraws the old view's columns by the ordinary rule.
    /// </summary>
    [Export] public bool Reset { get; set; }

    public List<Ref> Added { get; } = new();

    public List<ChunkKey> Removed { get; } = new();

    public static ChunkManifestSMSG FromProto(global::Bnet.ChunkManifestSMSG proto)
    {
      var manifest = new ChunkManifestSMSG { Reset = proto.Reset };

      foreach (var added in proto.Added)
      {
        manifest.Added.Add(new Ref(ChunkKey.FromProto(added.Pos), added.Revision));
      }

      foreach (var removed in proto.Removed)
      {
        manifest.Removed.Add(ChunkKey.FromProto(removed));
      }

      return manifest;
    }

    public override string ToString() =>
      $"ChunkManifest[reset={Reset}, +{Added.Count}, -{Removed.Count}]";
  }
}
