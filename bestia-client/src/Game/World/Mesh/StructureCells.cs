using System.Collections.Generic;
using BestiaBehemothClient.Bnet.Message.Map;

namespace BestiaBehemothClient.Game.World.Mesh
{
  /// <summary>
  /// The cells of the building pieces this client holds, for <see cref="WalkRules"/>: the client's side of the
  /// server's <c>StructureCellIndex</c>.
  /// </summary>
  /// <remarks>
  /// Filled from the static entity batches, which are the whole truth about a column, so a column's pieces are
  /// replaced rather than merged. A piece can fill cells outside its own column, so cells are kept by world
  /// position with the pieces that fill them.
  /// </remarks>
  public sealed class StructureCells
  {
    private readonly record struct Position(long X, long Y, long Z);

    private readonly Dictionary<ChunkKey, List<long>> _piecesByColumn = new();
    private readonly Dictionary<long, List<StructurePieces.Cell>> _cellsByPiece = new();
    private readonly Dictionary<Position, List<long>> _piecesByCell = new();
    private readonly Dictionary<Position, (int Fill, int Edges)> _merged = new();

    public int PieceCount => _cellsByPiece.Count;

    /// <summary>Puts a column's pieces in, replacing those it had.</summary>
    public void ReplaceColumn(ChunkKey column, IEnumerable<ChunkStaticEntitiesSMSG.Entry> entries)
    {
      DropColumn(column);

      var pieces = new List<long>();
      foreach (var entry in entries)
      {
        if (!StructurePieces.IsPiece(entry.Kind))
        {
          continue;
        }

        // Entry.Position is in Godot's axis order: its z is the server's y, and its y is up.
        var cells = StructurePieces.Rasterize(
          entry.Kind, entry.Position.X, entry.Position.Z, entry.Position.Y, StructurePieces.QuarterTurnsOf(entry.Yaw));
        Add(entry.EntityId, cells);
        pieces.Add(entry.EntityId);
      }

      if (pieces.Count > 0)
      {
        _piecesByColumn[column] = pieces;
      }
    }

    public void DropColumn(ChunkKey column)
    {
      if (!_piecesByColumn.Remove(column, out var pieces))
      {
        return;
      }

      foreach (var piece in pieces)
      {
        RemovePiece(piece);
      }
    }

    /// <summary>Takes one piece out. Safe to call for an id that is not a piece.</summary>
    public void Remove(ChunkKey column, long entityId)
    {
      if (_piecesByColumn.TryGetValue(column, out var pieces))
      {
        pieces.Remove(entityId);
      }

      RemovePiece(entityId);
    }

    public void Clear()
    {
      _piecesByColumn.Clear();
      _cellsByPiece.Clear();
      _piecesByCell.Clear();
      _merged.Clear();
    }

    /// <summary>How full a cell is from the bottom up, 0 to 255; 0 where nothing is built.</summary>
    public int FillAt(long x, long y, long z) => _merged.TryGetValue(new Position(x, y, z), out var cell) ? cell.Fill : 0;

    /// <summary>The walls on a cell's borders, as <c>StructurePieces.Edge*</c> bits.</summary>
    public int EdgesAt(long x, long y, long z) => _merged.TryGetValue(new Position(x, y, z), out var cell) ? cell.Edges : 0;

    private void Add(long piece, List<StructurePieces.Cell> cells)
    {
      _cellsByPiece[piece] = cells;
      foreach (var cell in cells)
      {
        var at = new Position(cell.X, cell.Y, cell.Z);
        if (!_piecesByCell.TryGetValue(at, out var pieces))
        {
          pieces = new List<long>();
          _piecesByCell[at] = pieces;
        }

        pieces.Add(piece);
        Merge(at);
      }
    }

    private void RemovePiece(long piece)
    {
      if (!_cellsByPiece.Remove(piece, out var cells))
      {
        return;
      }

      foreach (var cell in cells)
      {
        var at = new Position(cell.X, cell.Y, cell.Z);
        var pieces = _piecesByCell[at];
        pieces.Remove(piece);
        if (pieces.Count == 0)
        {
          _piecesByCell.Remove(at);
        }

        Merge(at);
      }
    }

    /// <summary>Two pieces in one cell share it, as on the server: the fuller fill wins and the walls add up.</summary>
    private void Merge(Position at)
    {
      if (!_piecesByCell.TryGetValue(at, out var pieces))
      {
        _merged.Remove(at);
        return;
      }

      var fill = 0;
      var edges = 0;
      foreach (var piece in pieces)
      {
        foreach (var cell in _cellsByPiece[piece])
        {
          if (cell.X != at.X || cell.Y != at.Y || cell.Z != at.Z)
          {
            continue;
          }

          fill = System.Math.Max(fill, cell.Fill);
          edges |= cell.Edges;
        }
      }

      _merged[at] = (fill, edges);
    }
  }
}
