using System;
using System.Collections.Generic;
using BestiaBehemothClient.Bnet.Message;
using BestiaBehemothClient.Bnet.Message.Map;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Drives the client half of chunk streaming: answers manifests, decodes payloads, applies patches, and
  /// reports what it holds.
  /// </summary>
  /// <remarks>
  /// Attach as a child of whatever node owns the <see cref="BnetSocket"/> and set
  /// <see cref="SocketPath"/>, or call <see cref="Attach"/> directly.
  ///
  /// <para>
  /// Rendering is not this class's job and is not done anywhere yet - the deliverable for this step is a text
  /// report proving the wire format works end to end. <see cref="Store"/> is the substrate any later mesh
  /// building sits on.
  /// </para>
  ///
  /// <para><b>Decoding is budgeted.</b> <c>BnetSocket._Process</c> drains its whole receive queue in one frame,
  /// so a login that streams a hundred and twenty chunks would hand them all over at once. Inflating and
  /// decoding one is a couple of hundred microseconds and a half-megabyte allocation, so doing them all in one
  /// frame is a visible hitch. They queue here and <see cref="DecodesPerFrame"/> get done per frame instead.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class ChunkStreamManager : Node
  {
    [Export] public NodePath SocketPath { get; set; }

    /// <summary>
    /// Chunks decoded per frame. Four at sixty frames a second clears a full view volume in about half a
    /// second, which comfortably outpaces what the server's own send budget delivers.
    /// </summary>
    [Export] public int DecodesPerFrame { get; set; } = 4;

    /// <summary>Print a line per chunk. Useful while there is nothing to look at; noisy once there is.</summary>
    [Export] public bool VerboseChunkLog { get; set; } = true;

    public ClientChunkStore Store { get; } = new();

    public WorldInfoSMSG WorldInfo { get; private set; }

    public BlockPaletteSMSG Palette { get; private set; }

    private BnetSocket _socket;

    private readonly Queue<ChunkDataSMSG> _toDecode = new();

    private int _decoded;
    private int _patched;
    private long _payloadBytes;
    private long _decodedBytes;

    public override void _Ready()
    {
      if (SocketPath != null && !SocketPath.IsEmpty)
      {
        Attach(GetNode<BnetSocket>(SocketPath));
      }
    }

    public void Attach(BnetSocket socket)
    {
      if (socket == null)
      {
        GD.PushWarning("ChunkStreamManager has no BnetSocket; chunk streaming will do nothing.");
        return;
      }

      _socket = socket;
      _socket.MessageReceived += OnMessageReceived;
    }

    public override void _ExitTree()
    {
      if (_socket != null)
      {
        _socket.MessageReceived -= OnMessageReceived;
      }
    }

    public override void _Process(double delta)
    {
      var budget = Math.Max(1, DecodesPerFrame);

      for (var done = 0; done < budget && _toDecode.Count > 0; done++)
      {
        Decode(_toDecode.Dequeue());
      }
    }

    private void OnMessageReceived(ISMSG message)
    {
      switch (message)
      {
        case WorldInfoSMSG info:
          OnWorldInfo(info);
          break;

        case BlockPaletteSMSG palette:
          Palette = palette;
          GD.Print($"[world] palette: {palette.Count} materials");
          break;

        case ChunkManifestSMSG manifest:
          OnManifest(manifest);
          break;

        case ChunkDataSMSG data:
          _toDecode.Enqueue(data);
          break;

        case ChunkPatchSMSG patch:
          OnPatch(patch);
          break;
      }
    }

    private void OnWorldInfo(WorldInfoSMSG info)
    {
      WorldInfo = info;
      GD.Print($"[world] {info}");

      if (info.ChunkFormatVersion != RleCodec.Version)
      {
        // The one version component that matters to a client which does not generate its own terrain: a
        // pipeline or palette mismatch would give it different rock, but a format mismatch means it cannot
        // read a single chunk. Say so plainly instead of failing later, once per chunk, inside the decoder.
        GD.PushError(
          $"[world] server sends chunk format {info.ChunkFormatVersion}, this client reads " +
          $"{RleCodec.Version}. No chunk will decode until the client is updated.");
      }

      // A new world, or a reconnect: nothing held can be assumed to still be right.
      Store.Clear();
      _toDecode.Clear();
    }

    private void OnManifest(ChunkManifestSMSG manifest)
    {
      var wanted = Store.Reconcile(manifest);

      GD.Print(
        $"[manifest] reset={manifest.Reset} +{manifest.Added.Count} -{manifest.Removed.Count}: " +
        $"holding {Store.HeldCount}, requesting {wanted.Count}");

      if (wanted.Count == 0 || _socket == null)
      {
        return;
      }

      // One message for the lot. The server queues them and serves a few per tick, so there is nothing gained
      // by pacing the asking as well as the answering.
      _socket.SendMessage(new ChunkRequestCMSG(wanted));
    }

    private void Decode(ChunkDataSMSG data)
    {
      try
      {
        var chunk = data.Decode();
        Store.Put(data.Key, chunk, data.Revision);

        _decoded++;
        _payloadBytes += data.PayloadBytes;
        _decodedBytes += chunk.Volume * 2;

        if (VerboseChunkLog)
        {
          GD.Print(
            $"[chunk] {data.Key} rev {data.Revision}  {data.PayloadBytes} B" +
            $"{(data.Deflated ? " deflated" : "")} -> {chunk.Volume} voxels\n" +
            $"        {Store.Describe(data.Key, Palette)}");
        }
      }
      catch (Exception ex)
      {
        // A payload that will not decode is not something to retry: it would fail identically. Report it and
        // leave the chunk unheld, so the next manifest offers it again.
        GD.PushError($"[chunk] {data.Key} rev {data.Revision} failed to decode: {ex.Message}");
      }
    }

    private void OnPatch(ChunkPatchSMSG patch)
    {
      if (Store.ApplyPatch(patch))
      {
        _patched++;

        if (VerboseChunkLog)
        {
          var edits = patch.Edits.Length / 5;
          GD.Print(
            $"[patch] {patch.Key} rev {patch.FromRevision}->{patch.ToRevision}  " +
            $"{patch.Edits.Length} B, up to {edits} edits\n" +
            $"        {Store.Describe(patch.Key, Palette)}");
        }

        return;
      }

      // Either the chunk was never held, or it diverged and ApplyPatch dropped it. Both are fixed the same
      // way, and only if the server still considers it ours.
      if (Store.IsAnnounced(patch.Key) && _socket != null)
      {
        GD.Print($"[patch] {patch.Key} not applicable; re-requesting the chunk");
        _socket.SendMessage(new ChunkRequestCMSG(new[] { patch.Key }));
      }
    }

    /// <summary>A one-line summary of the session so far, for a debug overlay or the console.</summary>
    public string Summary()
    {
      var ratio = _decodedBytes == 0 ? 0.0 : (double)_decodedBytes / Math.Max(1, _payloadBytes);

      return $"chunks held {Store.HeldCount}/{Store.AnnouncedCount} announced, " +
             $"{_decoded} decoded, {_patched} patched, " +
             $"{_payloadBytes / 1024} kB received for {_decodedBytes / 1024} kB of voxels ({ratio:F1}x)";
    }
  }
}
