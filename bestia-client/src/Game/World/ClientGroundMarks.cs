using System;
using System.Collections.Generic;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>What <see cref="ClientGroundMarks"/> has to ask of the world it lays marks in.</summary>
  public interface IGroundWorld
  {
    /// <summary>Real milliseconds on a clock that only moves forward. Prints age on it.</summary>
    long NowMillis { get; }

    /// <summary>The Bestia second wear and blood decay on, or -1 while the world clock is not anchored.</summary>
    long NowSecond { get; }

    /// <summary>Whether the client holds any of the column, which is what the server's residency check mirrors.</summary>
    bool HoldsColumn(ChunkKey column);

    int SurfaceBlockAt(long voxelX, long voxelY, int nearZ);
  }

  /// <summary>What <see cref="ClientGroundMarks"/> tells whatever draws the marks.</summary>
  public interface IGroundMarkSink
  {
    /// <summary>A print was laid; press it into the field without rebuilding.</summary>
    void StampPressed(ChunkKey column, GroundStamp stamp);

    /// <summary>A column's prints were replaced or aged; the field has to be rebuilt from the records.</summary>
    void StampsChanged(ChunkKey column);

    /// <summary>One cell's drawn level moved, and nothing else in its column did.</summary>
    void LevelChanged(ChunkKey column, int cellIndex, GroundLayers.Id layer, int level);

    /// <summary>A column's cells changed in ways a single cell cannot say; rewrite all of them.</summary>
    void LevelsChanged(ChunkKey column);

    /// <summary>A column is no longer held, so nothing of it may stay drawn.</summary>
    void ColumnDropped(ChunkKey column);
  }

  /// <summary>Where the disturbance field reads the prints it is rebuilt from.</summary>
  public interface IGroundStampSource
  {
    /// <summary>Every live print, each at the strength it should be drawn at now.</summary>
    void ForEachStamp(Action<ChunkKey, GroundStamp> visit);
  }

  /// <summary>What a layers message did to the levels this client had derived. Counted for the drift log.</summary>
  public readonly struct LayersMerge
  {
    public LayersMerge(LevelsMerge worn, LevelsMerge bloodied)
    {
      Worn = worn;
      Bloodied = bloodied;
    }

    public LevelsMerge Worn { get; }

    public LevelsMerge Bloodied { get; }
  }

  /// <summary>
  /// Everything this client knows about the lasting marks on the columns it holds: the single owner of the
  /// records.
  /// </summary>
  /// <remarks>
  /// Footprints, wear and blood are laid here from what the client sees walk and die and replaced by the
  /// server's snapshots; scorch is kept as sent. Nothing is laid before <see cref="SetRules"/>. Godot-free: the
  /// manager supplies the world and the sink.
  /// </remarks>
  public sealed class ClientGroundMarks : IGroundStampSource
  {
    private sealed class Column
    {
      internal ClientColumnStamps Stamps;
      internal ClientColumnLevels Worn;
      internal ClientColumnLevels Bloodied;

      /// <summary>Layers drawn as sent, by channel. The worn and bloodied channels are never set here.</summary>
      internal readonly byte[][] Nibbles = new byte[GroundLayers.Channels][];

      internal bool IsEmpty
      {
        get
        {
          if (Stamps != null || Worn != null || Bloodied != null)
          {
            return false;
          }

          foreach (var layer in Nibbles)
          {
            if (layer != null)
            {
              return false;
            }
          }

          return true;
        }
      }
    }

    private readonly int _size;
    private readonly IGroundWorld _world;
    private readonly IGroundMarkSink _sink;
    private readonly SurfaceBlockAt _blockAt;
    private readonly Dictionary<ChunkKey, Column> _columns = new();

    /// <summary>Reused between calls, so a footfall allocates nothing.</summary>
    private readonly List<CellAmount> _amounts = new();

    private readonly List<ChunkKey> _scratch = new();

    public ClientGroundMarks(int chunkSize, IGroundWorld world, IGroundMarkSink sink)
    {
      _size = chunkSize;
      _world = world;
      _sink = sink;
      _blockAt = world.SurfaceBlockAt;
    }

    /// <summary>Null until the server has sent its rules; nothing is laid before that.</summary>
    public GroundRules Rules { get; private set; }

    /// <summary>The world's chunk grid, so a mark across a seam lands in the column the server names.</summary>
    public ChunkWrap Wrap { get; set; } = ChunkWrap.None;

    /// <summary>The columns holding anything, for replaying to a renderer that attaches late.</summary>
    public IEnumerable<ChunkKey> Columns => _columns.Keys;

    public void SetRules(GroundRules rules) => Rules = rules;

    public void Clear() => _columns.Clear();

    /// <summary>
    /// Forgets a column that is no longer held.
    /// </summary>
    public void Drop(ChunkKey column)
    {
      if (_columns.Remove(column))
      {
        _sink.ColumnDropped(column);
      }
    }

    /// <summary>
    /// Replaces a column's prints with the server's, keeping the local ones it cannot have seen yet.
    /// </summary>
    public StampMerge ApplyStamps(ChunkKey column, GroundStamp[] snapshot)
    {
      if (Rules == null)
      {
        return default;
      }

      var held = _columns.TryGetValue(column, out var state);
      if (!held && snapshot.Length == 0)
      {
        return default;
      }

      state ??= Open(column);
      state.Stamps ??= new ClientColumnStamps(Rules.StampRingCapacity, Rules.StampLifetimeMillis);

      var merge = state.Stamps.ApplySnapshot(snapshot, _world.NowMillis);

      if (state.Stamps.IsEmpty)
      {
        state.Stamps = null;
      }

      Retire(column, state);
      _sink.StampsChanged(column);

      return merge;
    }

    /// <summary>
    /// Replaces each layer a message names and leaves the rest as they were.
    /// </summary>
    public LayersMerge ApplyLayers(ChunkKey column, IReadOnlyList<GroundLayerEntry> entries)
    {
      if (!_columns.TryGetValue(column, out var state))
      {
        state = Open(column);
      }

      var worn = default(LevelsMerge);
      var bloodied = default(LevelsMerge);

      foreach (var entry in entries)
      {
        switch (entry.Layer)
        {
          case GroundLayers.Id.Worn:
            worn = ReplaceLevels(ref state.Worn, entry, Rules?.Wear);
            break;

          case GroundLayers.Id.Bloodied:
            bloodied = ReplaceLevels(ref state.Bloodied, entry, Rules?.Blood);
            break;

          default:
            var channel = GroundLayers.ChannelOf(entry.Layer);
            if (channel >= 0)
            {
              state.Nibbles[channel] = entry.Nibbles;
            }

            break;
        }
      }

      Retire(column, state);
      _sink.LevelsChanged(column);

      return new LayersMerge(worn, bloodied);
    }

    /// <summary>
    /// The cells to draw for a column, one nibble array per channel, or null when nothing is marked.
    /// </summary>
    public byte[][] CellsOf(ChunkKey column)
    {
      if (!_columns.TryGetValue(column, out var state))
      {
        return null;
      }

      var cells = (byte[][])state.Nibbles.Clone();
      cells[GroundLayers.ChannelOf(GroundLayers.Id.Worn)] = state.Worn?.ToNibbles();
      cells[GroundLayers.ChannelOf(GroundLayers.Id.Bloodied)] = state.Bloodied?.ToNibbles();

      foreach (var layer in cells)
      {
        if (layer != null)
        {
          return cells;
        }
      }

      return null;
    }

    /// <summary>
    /// Lays what one step onto a tile leaves: a print, and wear on the tile and the four beside it.
    /// </summary>
    /// <remarks>
    /// Nothing happens where the surface block is unknown, which mirrors the server declining a column it has
    /// not built yet.
    /// </remarks>
    public void Footfall(long entityId, long fromX, long fromY, long toX, long toY, int nearZ)
    {
      if (Rules == null)
      {
        return;
      }

      var underfoot = _world.SurfaceBlockAt(toX, toY, nearZ);
      if (underfoot < 0)
      {
        return;
      }

      if (Rules.TakesPrint(underfoot))
      {
        LayPrint(entityId, fromX, fromY, toX, toY);
      }

      var now = _world.NowSecond;
      if (now < 0)
      {
        return;
      }

      // Every cell it names has a known block, which means its chunk is held: no residency check is needed.
      GroundSpread.Wear(Rules.Wear, toX, toY, nearZ, _blockAt, _amounts);
      AddAll(GroundLayers.Id.Worn, Rules.Wear, now, requireHeld: false);
    }

    /// <summary>Soaks the pool a death leaves into the ground around a tile.</summary>
    public void Death(long voxelX, long voxelY)
    {
      var now = _world.NowSecond;
      if (Rules == null || now < 0)
      {
        return;
      }

      GroundSpread.Blood(Rules.Blood, voxelX, voxelY, _amounts);
      AddAll(GroundLayers.Id.Bloodied, Rules.Blood, now, requireHeld: true);
    }

    /// <summary>Drops expired prints and tells the sink about every column whose prints visibly changed.</summary>
    public void AgeStamps()
    {
      var now = _world.NowMillis;

      CollectKeys();
      foreach (var key in _scratch)
      {
        var state = _columns[key];
        if (state.Stamps == null)
        {
          continue;
        }

        var changed = state.Stamps.Age(now);
        if (state.Stamps.IsEmpty)
        {
          state.Stamps = null;
        }

        Retire(key, state);

        if (changed)
        {
          _sink.StampsChanged(key);
        }
      }
    }

    /// <summary>Fades wear and blood to now, as the server's sweep does, and rewrites the columns that changed.</summary>
    public void AgeLevels()
    {
      var now = _world.NowSecond;
      if (Rules == null || now < 0)
      {
        return;
      }

      CollectKeys();
      foreach (var key in _scratch)
      {
        var state = _columns[key];

        var changed = AgeLayer(ref state.Worn, now);
        changed |= AgeLayer(ref state.Bloodied, now);

        Retire(key, state);

        if (changed)
        {
          _sink.LevelsChanged(key);
        }
      }
    }

    public void ForEachStamp(Action<ChunkKey, GroundStamp> visit)
    {
      var now = _world.NowMillis;

      foreach (var (key, state) in _columns)
      {
        var stamps = state.Stamps;
        if (stamps == null)
        {
          continue;
        }

        for (var i = 0; i < stamps.Count; i++)
        {
          if (stamps.IsLive(i, now))
          {
            visit(key, stamps.StampAt(i, now));
          }
        }
      }
    }

    private void LayPrint(long entityId, long fromX, long fromY, long toX, long toY)
    {
      var column = ColumnOf(toX, toY, out var cell);
      var state = _columns.TryGetValue(column, out var held) ? held : Open(column);

      state.Stamps ??= new ClientColumnStamps(Rules.StampRingCapacity, Rules.StampLifetimeMillis);

      var octant = GroundFootfall.OctantOf(toX - fromX, toY - fromY);
      var seed = GroundFootfall.SeedOf(entityId, toX, toY);

      if (state.Stamps.Add(cell, octant, seed, _world.NowMillis))
      {
        _sink.StampPressed(column, new GroundStamp(
          cell, GroundStampCells.Footprint, octant, seed, ClientColumnStamps.StrengthOf(ClientColumnStamps.Levels)));
      }
    }

    private void AddAll(GroundLayers.Id layer, LevelRules rules, long nowSecond, bool requireHeld)
    {
      foreach (var amount in _amounts)
      {
        var column = ColumnOf(amount.VoxelX, amount.VoxelY, out var cell);

        // The server marks only a column somebody holds, and the client's own held set is that set.
        if (requireHeld && !_world.HoldsColumn(column))
        {
          continue;
        }

        var state = _columns.TryGetValue(column, out var held) ? held : Open(column);

        var levels = layer == GroundLayers.Id.Worn
          ? (state.Worn ??= NewLevels(rules, nowSecond))
          : (state.Bloodied ??= NewLevels(rules, nowSecond));

        // Aged first, because the server does: a cell topped up every second would otherwise never fade here
        // either. Aging can move any cell, so then the whole column is rewritten rather than the one added to.
        var aged = levels.AgeTo(nowSecond);
        var moved = levels.Add(cell, amount.Amount);

        if (aged)
        {
          _sink.LevelsChanged(column);
        }
        else if (moved)
        {
          _sink.LevelChanged(column, cell, layer, levels.DisplayAt(cell));
        }
      }
    }

    private ClientColumnLevels NewLevels(LevelRules rules, long nowSecond) =>
      new(_size, rules.FadeSeconds, rules.VisibleThreshold, nowSecond);

    private LevelsMerge ReplaceLevels(ref ClientColumnLevels slot, GroundLayerEntry entry, LevelRules rules)
    {
      if (rules == null)
      {
        return default;
      }

      if (entry.Retires)
      {
        slot = null;

        return default;
      }

      if (entry.Levels == null)
      {
        return default;
      }

      if (slot != null)
      {
        return slot.Replace(entry.Levels, entry.LastDecayedSecond);
      }

      slot = new ClientColumnLevels(_size, rules.FadeSeconds, rules.VisibleThreshold, entry.LastDecayedSecond);
      slot.Replace(entry.Levels, entry.LastDecayedSecond);

      return default;
    }

    private static bool AgeLayer(ref ClientColumnLevels slot, long nowSecond)
    {
      if (slot == null)
      {
        return false;
      }

      var changed = slot.AgeTo(nowSecond);
      if (slot.IsEmpty)
      {
        slot = null;
      }

      return changed;
    }

    private Column Open(ChunkKey column)
    {
      var state = new Column();
      _columns[column] = state;

      return state;
    }

    private void Retire(ChunkKey column, Column state)
    {
      if (state.IsEmpty)
      {
        _columns.Remove(column);
      }
    }

    private void CollectKeys()
    {
      _scratch.Clear();
      _scratch.AddRange(_columns.Keys);
    }

    /// <summary>The column a tile is in, folded across a world seam, and its cell index within it.</summary>
    private ChunkKey ColumnOf(long voxelX, long voxelY, out int cellIndex)
    {
      var chunkX = Math.DivRem(voxelX, _size, out var localX);
      var chunkY = Math.DivRem(voxelY, _size, out var localY);

      // DivRem truncates towards zero, and the column left of the origin is -1 rather than 0.
      if (localX < 0)
      {
        chunkX--;
        localX += _size;
      }

      if (localY < 0)
      {
        chunkY--;
        localY += _size;
      }

      cellIndex = (int)(localY * _size + localX);

      return Wrap.Normalise(new ChunkKey((int)chunkX, (int)chunkY, 0));
    }
  }
}
