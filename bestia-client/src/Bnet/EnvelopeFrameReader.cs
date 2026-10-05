using System;
using System.Buffers.Binary;
using System.IO;

namespace BestiaBehemothClient.Bnet.Message
{
  /// <summary>
  /// Cuts the server's frames, a big-endian four-byte length and then the envelope, out of the byte stream.
  /// </summary>
  /// <remarks>
  /// Frames are handed out where they lie in the buffer, and only an unfinished frame is moved, once per read.
  /// So a large frame arriving in many small reads is copied once rather than once per read.
  /// </remarks>
  public sealed class EnvelopeFrameReader
  {
    public delegate void FrameHandler(ReadOnlySpan<byte> body);

    private const int LengthFieldBytes = 4;

    private readonly int _maxFrameLength;
    private byte[] _buffer = new byte[8192];
    private int _length;

    public EnvelopeFrameReader(int maxFrameLength)
    {
      _maxFrameLength = maxFrameLength;
    }

    /// <summary>
    /// Takes the next bytes of the stream and hands every frame they complete to <paramref name="onFrame"/>, in
    /// order. A body is only valid during its call.
    /// </summary>
    /// <exception cref="InvalidDataException">
    /// A frame claims a length outside 0 to the limit. The stream cannot be resynchronised after that, because
    /// there is no way to know where the next real frame begins.
    /// </exception>
    public void Feed(ReadOnlySpan<byte> data, FrameHandler onFrame)
    {
      Append(data);

      int offset = 0;
      while (_length - offset >= LengthFieldBytes)
      {
        int bodyLength = BinaryPrimitives.ReadInt32BigEndian(_buffer.AsSpan(offset, LengthFieldBytes));
        if (bodyLength < 0 || bodyLength > _maxFrameLength)
        {
          throw new InvalidDataException(
            $"A frame claims {bodyLength} bytes, outside the limit of {_maxFrameLength}.");
        }

        if (_length - offset - LengthFieldBytes < bodyLength)
        {
          break;
        }

        onFrame(_buffer.AsSpan(offset + LengthFieldBytes, bodyLength));
        offset += LengthFieldBytes + bodyLength;
      }

      KeepFrom(offset);
    }

    private void Append(ReadOnlySpan<byte> data)
    {
      if (_buffer.Length - _length < data.Length)
      {
        Array.Resize(ref _buffer, Math.Max(_buffer.Length * 2, _length + data.Length));
      }

      data.CopyTo(_buffer.AsSpan(_length));
      _length += data.Length;
    }

    private void KeepFrom(int offset)
    {
      if (offset == 0)
      {
        return;
      }

      Buffer.BlockCopy(_buffer, offset, _buffer, 0, _length - offset);
      _length -= offset;
    }
  }
}
