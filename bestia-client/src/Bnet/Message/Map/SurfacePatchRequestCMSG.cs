using System.Collections.Generic;
using BestiaBehemothClient.Game.World;
using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// Asks for the payloads of coarse patches this client does not already hold.
  /// </summary>
  /// <remarks>
  /// A client with a warm disk cache asks for far less than the manifest offers, and on a second visit to a
  /// region it asks for nothing at all - which is the point of a product that never changes.
  /// </remarks>
  public partial class SurfacePatchRequestCMSG : ICMSG
  {
    public List<PatchKey> Patches { get; } = new();

    public SurfacePatchRequestCMSG()
    {
    }

    public SurfacePatchRequestCMSG(IEnumerable<PatchKey> patches) => Patches.AddRange(patches);

    public override Envelope ToEnvelope()
    {
      var request = new global::Bnet.SurfacePatchRequestCMSG();

      foreach (var patch in Patches)
      {
        request.Patches.Add(patch.ToProto());
      }

      return new Envelope { SurfacePatchRequest = request };
    }

    public override string ToString() => $"SurfacePatchRequest[{Patches.Count} patches]";
  }
}
