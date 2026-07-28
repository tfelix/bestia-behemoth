using System.Collections.Generic;
using BestiaBehemothClient.Game.World;
using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// Asks for the payloads of chunks not held at the revision the manifest announced.
  /// </summary>
  /// <remarks>
  /// Also the repair path: a chunk whose patch did not line up with what was held gets discarded and
  /// re-requested here.
  ///
  /// <para>
  /// Only positions the current manifest offered are served. Asking for anything else is silently dropped, so
  /// there is no reason to try.
  /// </para>
  /// </remarks>
  public partial class ChunkRequestCMSG : ICMSG
  {
    public List<ChunkKey> Chunks { get; } = new();

    public ChunkRequestCMSG()
    {
    }

    public ChunkRequestCMSG(IEnumerable<ChunkKey> chunks) => Chunks.AddRange(chunks);

    public override Envelope ToEnvelope()
    {
      var request = new global::Bnet.ChunkRequestCMSG();

      foreach (var chunk in Chunks)
      {
        request.Chunks.Add(chunk.ToProto());
      }

      return new Envelope { ChunkRequest = request };
    }

    public override string ToString() => $"ChunkRequest[{Chunks.Count} chunks]";
  }
}
