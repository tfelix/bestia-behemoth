using Godot;
using System;
using System.Collections.Concurrent;
using System.Net.Sockets;
using System.Threading;
using System.IO;
using Bnet;
using Google.Protobuf;

namespace BestiaBehemothClient.Bnet.Message
{
  public enum ConnectionStatus
  {
    Disconnected,
    Connected,

    Connecting
  }

  /// <summary>
  /// Socket node which keeps the connection to the Bestia Behemoth server.
  /// Receives and buffers incoming messages to emit them and also sends protobuf messages to the server.
  /// </summary>
  [GlobalClass]
  public partial class BnetSocket : Node
  {
    [Export]
    public string ServerName { get; set; } = "localhost";

    [Export]
    public int Port { get; set; } = 8090;

    [Signal]
    public delegate void MessageReceivedEventHandler(ISMSG message);

    [Signal]
    public delegate void ConnectionStatusChangedEventHandler(ConnectionStatus status);

    private TcpClient _tcpClient;
    private NetworkStream _networkStream;
    private ConcurrentQueue<Envelope> _messageQueue;
    private Thread _socketThread;
    private volatile bool _shouldStop = false;
    private readonly object _connectionLock = new();
    private ConnectionStatus _currentStatus = ConnectionStatus.Disconnected;

    // Buffer for reading network data
    private MemoryStream _receiveBuffer;
    private readonly object _bufferLock = new();

    public override void _Ready()
    {
      _messageQueue = new ConcurrentQueue<Envelope>();
      _receiveBuffer = new MemoryStream();
    }

    public override void _Process(double delta)
    {
      // Process any complete messages from the queue
      while (_messageQueue.TryDequeue(out Envelope envelope))
      {
        if (envelope.Disconnected != null)
        {
          GD.Print($"Disconnected by server: {envelope.Disconnected.Reason}");
          DisconnectFromServer();
        }
        else if (envelope.AuthenticationSuccess != null)
        {
          var msg = new AuthenticationSuccess();
          EmitSignal(SignalName.MessageReceived, msg);
        }
        else if (envelope.Pong != null)
        {
          var msg = new Pong();
          EmitSignal(SignalName.MessageReceived, msg);
        }
        else if (envelope.Master != null)
        {
          var msg = Master.MasterSMSG.FromProto(envelope.Master);
          EmitSignal(SignalName.MessageReceived, msg);
        }
        else if (envelope.CompBestiaVisual != null)
        {
          var msg = Entity.BestiaVisualComponent.FromProto(envelope.CompBestiaVisual);
          EmitSignal(SignalName.MessageReceived, msg);
        }
        else if (envelope.Self != null)
        {
          var msg = Master.SelfSMSG.FromProto(envelope.Self);
          EmitSignal(SignalName.MessageReceived, msg);
        }
        else if (envelope.CompMasterVisual != null)
        {
          var msg = Entity.MasterVisualComponentSMSG.FromProto(envelope.CompMasterVisual);
          EmitSignal(SignalName.MessageReceived, msg);
        }
        else if (envelope.CompPosition != null)
        {
          var msg = Entity.PositionComponent.FromProto(envelope.CompPosition);
          EmitSignal(SignalName.MessageReceived, msg);
        }
        else if (envelope.CompPath != null)
        {
          var msg = Entity.PathComponentSMSG.FromProto(envelope.CompPath);
          EmitSignal(SignalName.MessageReceived, msg);
        }
        else if (envelope.CompSpeed != null)
        {
          var msg = Entity.SpeedComponentSMSG.FromProto(envelope.CompSpeed);
          EmitSignal(SignalName.MessageReceived, msg);
        }
        else if (envelope.OperationSuccess != null)
        {
          var msg = OperationSuccess.FromProto(envelope.OperationSuccess);
          EmitSignal(SignalName.MessageReceived, msg);
        }
        else if (envelope.OperationError != null)
        {
          var msg = OperationError.FromProto(envelope.OperationError);
          EmitSignal(SignalName.MessageReceived, msg);
        }
        else if (envelope.ChatSmsg != null)
        {
          var msg = System.ChatSMSG.FromProto(envelope.ChatSmsg);
          EmitSignal(SignalName.MessageReceived, msg);
        }
        else
        {
          GD.PrintErr("BnetSocket: Envelope message was not handled! Please add handling and type conversion.");
        }

        GD.Print("BnetSocket RX: ", envelope.ToString());
      }
    }

    public void SendMessage(ICMSG message)
    {
      var envelope = message.ToEnvelope();
      SendEnvelope(envelope);
    }

    /// <summary>
    /// Connects to the server in a background thread
    /// </summary>
    public Error ConnectToServer()
    {
      lock (_connectionLock)
      {
        if (_currentStatus == ConnectionStatus.Connected)
        {
          GD.Print("Already connected to server");
          return Error.AlreadyInUse;
        }

        if (_socketThread != null && _socketThread.IsAlive)
        {
          GD.Print("Connection already in progress");
          return Error.AlreadyInUse;
        }

        GD.Print($"Starting connection to {ServerName}:{Port}");
        _shouldStop = false;

        SetConnectionStatus(ConnectionStatus.Connecting);

        _socketThread = new Thread(SocketThreadWorker)
        {
          IsBackground = true,
          Name = "BnetSocket"
        };
        _socketThread.Start();

        return Error.Ok;
      }
    }

    public void DisconnectFromServer()
    {
      lock (_connectionLock)
      {
        _shouldStop = true;
        SetConnectionStatus(ConnectionStatus.Disconnected);

        try
        {
          _networkStream?.Close();
          _tcpClient?.Close();
        }
        catch (Exception ex)
        {
          GD.PrintErr($"Error during disconnect: {ex.Message}");
        }

        if (_socketThread != null && _socketThread.IsAlive)
        {
          if (!_socketThread.Join(1000)) // Wait up to 1 second
          {
            GD.PrintErr("Socket thread did not terminate gracefully");
          }
        }

        GD.Print("Disconnected from server");
      }
    }

    public bool IsConnected()
    {
      return _currentStatus == ConnectionStatus.Connected && _tcpClient != null && _tcpClient.Connected;
    }

    private void SetConnectionStatus(ConnectionStatus newStatus)
    {
      if (_currentStatus != newStatus)
      {
        _currentStatus = newStatus;
        // Emit the signal from the main thread
        CallDeferred(nameof(EmitConnectionStatusChanged));
      }
    }

    private void EmitConnectionStatusChanged()
    {
      EmitSignal(SignalName.ConnectionStatusChanged, (int)_currentStatus);
    }

    private void SocketThreadWorker()
    {
      try
      {
        // Connect to server
        _tcpClient = new TcpClient();
        GD.Print($"Attempting to connect to {ServerName}:{Port}");

        _tcpClient.Connect(ServerName, Port);
        _networkStream = _tcpClient.GetStream();

        // Set a read timeout so the thread can check for exit requests
        _tcpClient.ReceiveTimeout = 500; // 500 ms timeout

        GD.Print("Successfully connected to server");
        SetConnectionStatus(ConnectionStatus.Connected);

        // Buffer for reading data
        byte[] buffer = new byte[4096];

        // Main communication loop
        while (!_shouldStop && _tcpClient.Connected)
        {
          try
          {
            // Blocking read with timeout
            int bytesRead = _networkStream.Read(buffer, 0, buffer.Length);
            if (bytesRead > 0)
            {
              lock (_bufferLock)
              {
                // Append new data to the receive buffer
                _receiveBuffer.Write(buffer, 0, bytesRead);

                // Try to extract complete messages
                ProcessReceivedData();
              }
            }
            else
            {
              // bytesRead == 0 means the connection was closed by the remote host
              GD.Print("Connection closed by remote host");
              break;
            }
          }
          catch (IOException ex) when (ex.InnerException is SocketException socketEx &&
                                       socketEx.SocketErrorCode == SocketError.TimedOut)
          {
            // Timeout occurred - this is normal, just continue the loop to check _shouldStop
            continue;
          }
          catch (Exception ex)
          {
            GD.PrintErr($"Error reading data: {ex.Message}");
            break;
          }
        }
      }
      catch (Exception ex)
      {
        GD.PrintErr($"Socket thread error: {ex.Message}");
        SetConnectionStatus(ConnectionStatus.Disconnected);
      }
      finally
      {
        SetConnectionStatus(ConnectionStatus.Disconnected);
        try
        {
          _networkStream?.Close();
          _tcpClient?.Close();
        }
        catch (Exception ex)
        {
          GD.PrintErr($"Error cleaning up socket: {ex.Message}");
        }
        GD.Print("Socket thread terminated");
      }
    }

    /// <summary>
    /// Processes the received data buffer to extract complete messages with length prefixes
    /// </summary>
    private void ProcessReceivedData()
    {
      byte[] data = _receiveBuffer.ToArray();
      _receiveBuffer.SetLength(0); // Clear the buffer
      _receiveBuffer.Position = 0;

      int offset = 0;

      while (offset < data.Length)
      {
        // Check if we have at least 4 bytes for the length prefix
        if (offset + 4 > data.Length)
        {
          // Not enough data for length prefix, put remaining bytes back in buffer
          _receiveBuffer.Write(data, offset, data.Length - offset);
          break;
        }

        // Read the message length (big-endian 4 bytes)
        int messageLength = (data[offset] << 24) |
                           (data[offset + 1] << 16) |
                           (data[offset + 2] << 8) |
                           data[offset + 3];

        offset += 4;

        // Check if we have enough data for the complete message
        if (offset + messageLength > data.Length)
        {
          // Not enough data for complete message, put length prefix and remaining bytes back
          _receiveBuffer.Write(data, offset - 4, data.Length - (offset - 4));
          break;
        }

        // Extract the complete message
        byte[] messageBytes = new byte[messageLength];
        Array.Copy(data, offset, messageBytes, 0, messageLength);
        offset += messageLength;

        try
        {
          // Decode the protobuf message
          Envelope envelope = Envelope.Parser.ParseFrom(messageBytes);
          _messageQueue.Enqueue(envelope);

          if (_messageQueue.Count > 10)
          {
            GD.Print($"BnetSocket: Warning queue size is {_messageQueue.Count}");
          }
        }
        catch (Exception ex)
        {
          GD.PrintErr($"Failed to parse protobuf message: {ex.Message}");
        }
      }
    }

    private void SendEnvelope(Envelope envelope)
    {
      if (_currentStatus == ConnectionStatus.Connected && _networkStream != null)
      {
        try
        {
          // Serialize the protobuf message to a byte array
          using (var memoryStream = new MemoryStream())
          {
            using (var codedOutput = new CodedOutputStream(memoryStream))
            {
              envelope.WriteTo(codedOutput);
            }
            byte[] messageBytes = memoryStream.ToArray();

            // Create a buffer with big-endian length prefix (4 bytes) + message
            byte[] buffer = new byte[4 + messageBytes.Length];

            // Write the length prefix in big-endian format
            buffer[0] = (byte)((messageBytes.Length >> 24) & 0xFF);
            buffer[1] = (byte)((messageBytes.Length >> 16) & 0xFF);
            buffer[2] = (byte)((messageBytes.Length >> 8) & 0xFF);
            buffer[3] = (byte)(messageBytes.Length & 0xFF);

            // Copy the message bytes
            Array.Copy(messageBytes, 0, buffer, 4, messageBytes.Length);

            // Send the complete buffer
            _networkStream.Write(buffer, 0, buffer.Length);
            _networkStream.Flush();

            GD.Print("BnetSocket TX: ", envelope.ToString());
            // GD.Print($"Sent envelope message of {messageBytes.Length} bytes");
          }
        }
        catch (Exception ex)
        {
          GD.PrintErr($"Failed to send envelope: {ex.Message}");
        }
      }
      else
      {
        GD.PrintErr("Cannot send envelope: not connected to server");
      }
    }

    public override void _ExitTree()
    {
      DisconnectFromServer();
      _receiveBuffer?.Dispose();
    }
  }
}