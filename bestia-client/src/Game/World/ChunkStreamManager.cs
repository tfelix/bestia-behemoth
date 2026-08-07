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
  /// Rendering belongs to <see cref="TerrainRenderer"/>, which this class only tells what changed. The split is
  /// deliberate: everything here is about agreeing with the server on what is held, and none of it should have to
  /// know how a chunk is drawn.
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
    [Export] public bool VerboseChunkLog { get; set; } = false;

    /// <summary>
    /// The renderer to notify, or unset to stream without drawing anything.
    /// </summary>
    /// <remarks>
    /// Optional so that the decode path can still be exercised headlessly, which is how the wire format was
    /// verified before there was anything to look at.
    ///
    /// <para><b>Assigning this replays everything already received</b>, and that is not a convenience - it is
    /// the only thing that makes the renderer work at all. The server sends the world info the instant a
    /// connection authenticates, which is during master selection; the Game scene that owns the renderer does
    /// not exist until a master has been chosen. So the renderer is always attached *after* the message that
    /// configures it has come and gone, and a plain setter would leave it permanently unconfigured -
    /// silently, because an unconfigured renderer discards chunks rather than failing.
    /// </para>
    /// </remarks>
    [Export]
    public TerrainRenderer Renderer
    {
      get => IsUsable(_renderer) ? _renderer : null;

      set
      {
        _renderer = value;

        if (!IsUsable(value))
        {
          return;
        }

        value.Configure(Store, WorldInfo);

        // Anything decoded before the renderer existed is held but undrawn, and no further message will
        // mention it. Without this a player sees terrain only from wherever they happen to walk next.
        foreach (var key in Store.HeldKeys())
        {
          value.Invalidate(key);
        }
      }
    }

    private TerrainRenderer _renderer;

    /// <summary>
    /// Draws what stands on the terrain, or null in a headless test.
    /// </summary>
    /// <remarks>
    /// Nullable and separate from <see cref="Renderer"/> deliberately: the two have independent lifecycles on
    /// the wire - a chunk payload and its static batch are separate messages - and a client that can draw
    /// ground but has no prop meshes yet is a legitimate state during development.
    ///
    /// <para><b>Assigning this replays every batch still held</b>, for exactly the reason
    /// <see cref="Renderer"/> does, and it needs its own buffer to replay from because a static batch is not
    /// stored anywhere else. <see cref="Store"/> holds decoded terrain and can re-render it on demand; a prop
    /// batch is a one-shot message the server does not repeat until the column is re-materialised, so
    /// whatever arrived before the Game scene existed would otherwise be lost until the player wandered out
    /// of the view volume and back.
    /// </para>
    /// </remarks>
    [Export]
    public StaticEntityRenderer StaticEntities
    {
      get => IsUsable(_staticEntities) ? _staticEntities : null;

      set
      {
        _staticEntities = value;

        if (!IsUsable(value))
        {
          return;
        }

        value.Configure(WorldInfo);

        foreach (var batch in _staticBatches.Values)
        {
          value.Apply(batch);
        }
      }
    }

    private StaticEntityRenderer _staticEntities;

    /// <summary>
    /// The latest static batch for each chunk still held, so a late-attached renderer can be caught up.
    /// </summary>
    /// <remarks>
    /// Keyed and dropped in step with <see cref="Store"/> rather than by its own rule: a batch describes the
    /// contents of a column the client holds terrain for, so it stops being true at exactly the moment that
    /// terrain does.
    /// </remarks>
    private readonly Dictionary<ChunkKey, ChunkStaticEntitiesSMSG> _staticBatches = new();

    /// <summary>
    /// Whether a renderer reference is still safe to call.
    /// </summary>
    /// <remarks>
    /// The renderer belongs to the Game scene and is freed when that scene is torn down on logout, but this
    /// manager is an autoload and outlives it. A freed Godot object leaves its C# wrapper non-null, so
    /// <c>?.</c> does not protect against it and the next call throws <c>ObjectDisposedException</c> - on the
    /// second login of a session, which is a long way from the code that caused it.
    /// </remarks>
    private static bool IsUsable(Node renderer) =>
      renderer != null && GodotObject.IsInstanceValid(renderer);

    public ClientChunkStore Store { get; } = new();

    public WorldInfoSMSG WorldInfo { get; private set; }

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

        case ChunkManifestSMSG manifest:
          OnManifest(manifest);
          break;

        case ChunkDataSMSG data:
          _toDecode.Enqueue(data);
          break;

        case ChunkPatchSMSG patch:
          OnPatch(patch);
          break;

        case ChunkStaticEntitiesSMSG statics:
          // Applied straight away rather than queued: a batch is a few hundred transforms and, for the kinds
          // that have art, a scene instance each - not a decode and a mesh build, so it does not need the
          // frame budget the chunk queue exists to spread. It arrives behind its chunk, so the ground is
          // already there or already queued.
          _staticBatches[statics.Key] = statics;
          StaticEntities?.Apply(statics);
          break;

        case StaticEntityRemovedSMSG removed:
          OnStaticEntityRemoved(removed);
          break;
      }
    }

    /// <summary>
    /// Forgets one static entity a chunk we hold used to have.
    /// </summary>
    /// <remarks>
    /// Both halves matter and for different reasons. The renderer is what the player sees; the retained batch
    /// is what a late-attaching renderer is replayed from, so leaving a collected crystal in it would put it
    /// back on screen the next time <c>StaticEntities</c> is set.
    ///
    /// <para>
    /// Silently ignores an id we do not hold. That is the ordinary case for a batch already dropped by the
    /// manifest, and it is why the removal message can be treated as advisory: losing one leaves a prop drawn
    /// that nobody can pick up only until the column reloads, at which point the server omits it anyway.
    /// </para>
    /// </remarks>
    private void OnStaticEntityRemoved(StaticEntityRemovedSMSG removed)
    {
      if (!_staticBatches.TryGetValue(removed.Key, out var batch))
      {
        return;
      }

      var pruned = batch.Without(removed.EntityId);
      if (pruned == null)
      {
        return;
      }

      _staticBatches[removed.Key] = pruned;
      StaticEntities?.RemoveEntity(removed.Key, removed.EntityId);
    }

    private void OnWorldInfo(WorldInfoSMSG info)
    {
      WorldInfo = info;

      // Before anything else: a chunk or static-entity batch for this world can arrive the instant after this
      // message, and ChunkStaticEntitiesSMSG.FromProto expands local->global coordinates using this the moment
      // it decodes, from inside BnetSocket's dispatch, which has no world context of its own to pass instead.
      ChunkEngine.ChunkSize = info.ChunkSize;

      GD.Print($"[world] {info}");

      if (info.ChunkEngineVersion != ChunkEngine.Version)
      {
        // Said plainly here rather than discovered later, once per chunk, inside the decoder - and it covers
        // the quieter half too: a palette this client disagrees with decodes perfectly and draws the wrong
        // rock, which looks like a rendering bug rather than a version mismatch.
        GD.PushError(
          $"[world] server speaks chunk engine v{info.ChunkEngineVersion}, this client speaks " +
          $"v{ChunkEngine.Version}. Terrain will not decode, or will decode to the wrong materials, " +
          "until the client is updated.");
      }

      // A new world, or a reconnect: nothing held can be assumed to still be right.
      Store.Clear();
      _toDecode.Clear();
      _staticBatches.Clear();
      StaticEntities?.Clear();

      Renderer?.Configure(Store, info);
      StaticEntities?.Configure(info);
    }

    private void OnManifest(ChunkManifestSMSG manifest)
    {
      // A reset manifest can silently drop anything it does not re-list, so the candidates are everything held
      // rather than just what Removed names.
      var candidates = manifest.Reset ? Store.HeldKeys() : manifest.Removed;

      var wanted = Store.Reconcile(manifest);

      // Dropped after reconciling, so a reset manifest that re-lists a chunk does not tear down geometry it is
      // about to want back. Not gated on a renderer existing, unlike the terrain removal it used to sit
      // inside: the batch buffer has to be pruned whether or not anything is currently drawing, or a renderer
      // attached later would be replayed the contents of columns the client stopped holding long ago.
      foreach (var key in candidates)
      {
        if (Store.Get(key) != null)
        {
          continue;
        }

        Renderer?.Remove(key);
        _staticBatches.Remove(key);
        StaticEntities?.Remove(key);
      }

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
        Renderer?.Invalidate(data.Key);

        _decoded++;
        _payloadBytes += data.PayloadBytes;
        _decodedBytes += chunk.Volume * 2;

        if (VerboseChunkLog)
        {
          GD.Print(
            $"[chunk] {data.Key} rev {data.Revision}  {data.PayloadBytes} B" +
            $"{(data.Deflated ? " deflated" : "")} -> {chunk.Volume} voxels\n" +
            $"        {Store.Describe(data.Key)}");
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
        Renderer?.Invalidate(patch.Key);

        if (VerboseChunkLog)
        {
          GD.Print(
            $"[patch] {patch.Key} rev {patch.FromRevision}->{patch.ToRevision}  " +
            $"{patch.Removals.Length} B, {patch.RemovalCount} removals\n" +
            $"        {Store.Describe(patch.Key)}");
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
