using System;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>What a snapshot did to levels this client had derived itself. Counted for the drift log.</summary>
  public readonly struct LevelsMerge
  {
    public LevelsMerge(int prior, int drifted)
    {
      Prior = prior;
      Drifted = drifted;
    }

    /// <summary>Marked cells held before the snapshot.</summary>
    public int Prior { get; }

    /// <summary>Cells whose derived level was more than one level away from the server's.</summary>
    public int Drifted { get; }
  }

  /// <summary>
  /// How strongly one graded layer (worn ground, blood) marks each square metre of one chunk column, at the
  /// server's own resolution.
  /// </summary>
  /// <remarks>
  /// A byte per cell where the wire draws a nibble, because a client adding footfalls to nibbles would cross
  /// the visible threshold at a different step than the server. <see cref="AgeTo"/> is the server's
  /// <c>MarkedColumn.ageTo</c>, integer for integer. Godot-free so both can be pinned without an engine.
  /// </remarks>
  public sealed class ClientColumnLevels
  {
    /// <summary>The strongest a cell can be marked, being every bit of the byte it is stored in.</summary>
    public const int MaxLevel = 255;

    private readonly byte[] _levels;
    private readonly int _size;
    private readonly int _fadeSeconds;
    private readonly int _visibleThreshold;
    private int _marked;

    public ClientColumnLevels(int size, int fadeSeconds, int visibleThreshold, long lastDecayedSecond)
    {
      _size = size;
      _levels = new byte[size * size];
      _fadeSeconds = fadeSeconds;
      _visibleThreshold = visibleThreshold;
      LastDecayedSecond = lastDecayedSecond;
    }

    /// <summary>The Bestia second the levels have already faded up to. Time since is still owed.</summary>
    public long LastDecayedSecond { get; private set; }

    public bool IsEmpty => _marked == 0;

    public int this[int index] => _levels[index];

    /// <summary>What the shader reads for a cell: its level's top four bits, or nothing below the threshold.</summary>
    public int DisplayAt(int index) => DisplayOf(_levels[index]);

    /// <summary>
    /// Adds to one cell, saturating at <see cref="MaxLevel"/>. Age the column first, or traffic would hold a
    /// mark open and its clock would never run.
    /// </summary>
    /// <returns>whether the drawn level of that cell changed</returns>
    public bool Add(int index, int amount)
    {
      if (amount <= 0)
      {
        return false;
      }

      var before = _levels[index];
      if (before == 0)
      {
        _marked++;
      }

      var after = (byte)Math.Min(MaxLevel, before + amount);
      _levels[index] = after;

      return DisplayOf(before) != DisplayOf(after);
    }

    /// <summary>
    /// Fades the column forward to <paramref name="nowSecond"/>, keeping the remainder the integer steps leave.
    /// </summary>
    /// <remarks>
    /// Advances <see cref="LastDecayedSecond"/> only by the time the removed steps account for. Rounding it up to
    /// <paramref name="nowSecond"/> would discard the remainder every pass, and a full cell fading over days
    /// moves a quarter of a level a minute.
    /// </remarks>
    /// <returns>whether any cell's drawn level changed</returns>
    public bool AgeTo(long nowSecond)
    {
      var elapsed = nowSecond - LastDecayedSecond;
      if (elapsed <= 0)
      {
        return false;
      }

      var steps = elapsed * MaxLevel / _fadeSeconds;
      if (steps <= 0)
      {
        return false;
      }

      LastDecayedSecond += steps * _fadeSeconds / MaxLevel;

      return DecayBy((int)Math.Min(steps, MaxLevel));
    }

    /// <summary>
    /// Replaces every cell with the server's, and adopts its decay clock.
    /// </summary>
    /// <returns>what the derived levels got wrong, for the drift log</returns>
    public LevelsMerge Replace(byte[] levels, long lastDecayedSecond)
    {
      if (levels.Length != _levels.Length)
      {
        throw new ArgumentException($"a {_size}-wide column holds {_levels.Length} cells, got {levels.Length}");
      }

      var prior = _marked;
      var drifted = 0;
      _marked = 0;

      for (var i = 0; i < levels.Length; i++)
      {
        if (Math.Abs(_levels[i] - levels[i]) > 1)
        {
          drifted++;
        }

        _levels[i] = levels[i];
        if (levels[i] != 0)
        {
          _marked++;
        }
      }

      LastDecayedSecond = lastDecayedSecond;

      return new LevelsMerge(prior, drifted);
    }

    /// <summary>
    /// The drawn levels in <see cref="GroundLayerCells"/>'s nibble layout, or null when no cell is drawn at all.
    /// </summary>
    public byte[] ToNibbles()
    {
      byte[] packed = null;

      for (var i = 0; i < _levels.Length; i++)
      {
        var nibble = DisplayOf(_levels[i]);
        if (nibble == 0)
        {
          continue;
        }

        packed ??= new byte[GroundLayerCells.ByteLength(_size)];
        packed[i >> 1] |= (byte)((i & 1) == 0 ? nibble : nibble << 4);
      }

      return packed;
    }

    private bool DecayBy(int amount)
    {
      if (_marked == 0)
      {
        return false;
      }

      var redraw = false;

      for (var i = 0; i < _levels.Length; i++)
      {
        var before = _levels[i];
        if (before == 0)
        {
          continue;
        }

        var after = (byte)Math.Max(0, before - amount);
        _levels[i] = after;

        if (after == 0)
        {
          _marked--;
        }

        if (DisplayOf(before) != DisplayOf(after))
        {
          redraw = true;
        }
      }

      return redraw;
    }

    private int DisplayOf(int level) => level >= _visibleThreshold ? level >> 4 : 0;
  }
}
