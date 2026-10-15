using System.Collections.Generic;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>One square metre of a column that is burning, or burnt out a moment ago.</summary>
  public readonly struct FireCell
  {
    public FireCell(int index, float ignitedAt, float outAt)
    {
      Index = index;
      IgnitedAt = ignitedAt;
      OutAt = outAt;
    }

    /// <summary><c>localY * chunkSize + localX</c>, the layout of the server's burning mask.</summary>
    public int Index { get; }

    /// <summary>When this client first saw the cell alight, on <see cref="GroundFireClock"/>.</summary>
    public float IgnitedAt { get; }

    /// <summary>When the cell went out, or <see cref="GroundFireClock.Never"/> while it still burns.</summary>
    public float OutAt { get; }

    public bool IsBurning => OutAt == GroundFireClock.Never;
  }

  /// <summary>What <see cref="ClientGroundFire"/> tells whatever draws the fire.</summary>
  public interface IGroundFireSink
  {
    /// <summary>A column's cells caught, went out or were forgotten; redraw all of them.</summary>
    void FireChanged(ChunkKey column);
  }

  /// <summary>
  /// When each burning cell of the held columns caught fire and when it went out.
  /// </summary>
  /// <remarks>
  /// The server only says which cells are alight now. The shaders need the times to animate a burn, so they
  /// are taken here, when a cell's bit first sets and when it clears. A cell that went out is kept a little
  /// longer, so its flames can fade and its grass can finish burning down.
  /// </remarks>
  public sealed class ClientGroundFire
  {
    /// <summary>
    /// How long a cell is kept after it went out, so whatever animates its end can finish. Those effects name
    /// this constant where they are tuned.
    /// </summary>
    public const float KeptAfterOutSeconds = 6f;

    private readonly int _size;
    private readonly IGroundFireSink _sink;
    private readonly Dictionary<ChunkKey, Dictionary<int, FireCell>> _columns = new();
    private readonly List<int> _scratch = new();

    public ClientGroundFire(int chunkSize, IGroundFireSink sink)
    {
      _size = chunkSize;
      _sink = sink;
    }

    public IEnumerable<ChunkKey> Columns => _columns.Keys;

    /// <summary>
    /// Takes one column's burning mask, which is the whole truth about that column.
    /// </summary>
    /// <param name="burning">The server's bitmask, or null when nothing in the column is alight.</param>
    public void Apply(ChunkKey column, byte[] burning, float now)
    {
      column = column.Column;
      _columns.TryGetValue(column, out var cells);
      var changed = false;

      for (var index = 0; index < _size * _size; index++)
      {
        if (IsSet(burning, index) && !IsBurningIn(cells, index))
        {
          cells ??= _columns[column] = new Dictionary<int, FireCell>();
          cells[index] = new FireCell(index, now, GroundFireClock.Never);
          changed = true;
        }
      }

      if (cells != null)
      {
        _scratch.Clear();
        foreach (var cell in cells.Values)
        {
          if (cell.IsBurning && !IsSet(burning, cell.Index))
          {
            _scratch.Add(cell.Index);
          }
        }

        foreach (var index in _scratch)
        {
          cells[index] = new FireCell(index, cells[index].IgnitedAt, now);
          changed = true;
        }
      }

      if (changed)
      {
        _sink.FireChanged(column);
      }
    }

    /// <summary>Forgets the cells that went out longer than <see cref="KeptAfterOutSeconds"/> ago.</summary>
    public void Sweep(float now)
    {
      List<ChunkKey> emptied = null;

      foreach (var (column, cells) in _columns)
      {
        _scratch.Clear();
        foreach (var cell in cells.Values)
        {
          if (!cell.IsBurning && now - cell.OutAt >= KeptAfterOutSeconds)
          {
            _scratch.Add(cell.Index);
          }
        }

        if (_scratch.Count == 0)
        {
          continue;
        }

        foreach (var index in _scratch)
        {
          cells.Remove(index);
        }

        if (cells.Count == 0)
        {
          (emptied ??= new List<ChunkKey>()).Add(column);
        }

        _sink.FireChanged(column);
      }

      emptied?.ForEach(column => _columns.Remove(column));
    }

    /// <summary>Copies a column's cells into <paramref name="into"/>, which is cleared first.</summary>
    public void CellsOf(ChunkKey column, List<FireCell> into)
    {
      into.Clear();

      if (_columns.TryGetValue(column.Column, out var cells))
      {
        into.AddRange(cells.Values);
      }
    }

    /// <summary>Forgets a column that is no longer held.</summary>
    public void Drop(ChunkKey column)
    {
      if (_columns.Remove(column.Column))
      {
        _sink.FireChanged(column.Column);
      }
    }

    public void Clear() => _columns.Clear();

    private static bool IsBurningIn(Dictionary<int, FireCell> cells, int index) =>
      cells != null && cells.TryGetValue(index, out var cell) && cell.IsBurning;

    /// <summary>
    /// The server's bit order: byte <c>index / 8</c>, least significant bit first. The server's
    /// <c>ColumnMaskTest</c> and <c>ClientGroundFireTest</c> pin it from the same hand-typed bytes.
    /// </summary>
    private static bool IsSet(byte[] mask, int index) =>
      mask != null && (mask[index >> 3] >> (index & 7) & 1) == 1;
  }
}
