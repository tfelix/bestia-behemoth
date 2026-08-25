using System.Collections.Generic;
using BestiaBehemothClient.Game.World;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// Which coarse patches this client is entitled to hold.
  /// </summary>
  /// <remarks>
  /// The same announce-then-pull contract <see cref="ChunkManifestSMSG"/> has, minus revisions: a patch is a
  /// pure function of the server's heightfield, so one already held never has to be fetched again - not this
  /// session, and not the next one either.
  ///
  /// <para>
  /// It is also the server's authorisation set. Asking for a position it never offered is dropped, so there is
  /// no point asking for anything not listed here.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class SurfacePatchManifestSMSG : MapSMSG
  {
    /// <summary>Replace the whole held set rather than amending it. First manifest of a session only.</summary>
    [Export] public bool Reset { get; set; }

    public List<PatchKey> Added { get; } = new();

    public List<PatchKey> Removed { get; } = new();

    public static SurfacePatchManifestSMSG FromProto(global::Bnet.SurfacePatchManifestSMSG proto)
    {
      var manifest = new SurfacePatchManifestSMSG { Reset = proto.Reset };

      foreach (var added in proto.Added)
      {
        manifest.Added.Add(PatchKey.FromProto(added));
      }

      foreach (var removed in proto.Removed)
      {
        manifest.Removed.Add(PatchKey.FromProto(removed));
      }

      return manifest;
    }

    public override string ToString() =>
      $"SurfacePatchManifest[reset={Reset}, +{Added.Count}, -{Removed.Count}]";
  }
}
