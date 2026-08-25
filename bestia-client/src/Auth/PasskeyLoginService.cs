using System;
using System.Threading;
using System.Threading.Tasks;
using Godot;

namespace BestiaBehemothClient.Auth
{
  /// <summary>
  /// Drives signing in from the game's side: the passkey ceremony, and the silent resume that
  /// replaces it on every start after the first.
  /// </summary>
  /// <remarks>
  /// <para>
  /// The game's involvement in the ceremony is: bind a loopback port, ask the login server to open a
  /// session, hand the URL to the system browser, wait for the redirect, and trade the code for a
  /// token. WebAuthn itself - Windows Hello, Touch ID, iCloud Keychain, a security key, a phone over
  /// QR - happens inside the browser, which is what keeps this file free of platform code.
  /// </para>
  /// <para>
  /// A resume is one HTTP call and nothing else: no browser, no listener, no prompt. It works because
  /// the ceremony also hands out a long-lived token, which <see cref="RefreshTokenStore"/> keeps
  /// between runs. Both paths end in the same <see cref="LoginSucceededEventHandler"/> carrying the
  /// same kind of zone JWT, so the connection sequence after them is identical.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class PasskeyLoginService : Node
  {
    /// <summary>Carries the zone JWT, ready to be sent in the socket handshake.</summary>
    [Signal]
    public delegate void LoginSucceededEventHandler(string token);

    [Signal]
    public delegate void LoginFailedEventHandler(string reason);

    /// <summary>The browser has been opened and the callback is being waited for.</summary>
    [Signal]
    public delegate void AwaitingBrowserEventHandler();

    /// <summary>
    /// The stored session could not be resumed and has been discarded, so the full ceremony is the
    /// only way in. Distinct from <see cref="LoginFailedEventHandler"/> because this is the expected
    /// end of a token's life rather than something gone wrong: there is nothing to tell the player.
    /// </summary>
    [Signal]
    public delegate void ResumeUnavailableEventHandler();

    /// <summary>
    /// Matches `game-login.session-ttl-seconds` on the server. Waiting longer than the session can
    /// live only leaves a listener open for a code that can no longer be minted.
    /// </summary>
    [Export]
    public int TimeoutSeconds { get; set; } = 300;

    private CancellationTokenSource _cancellation;

    /// <summary>True while a login is in flight, so the UI can refuse to start a second one.</summary>
    public bool IsBusy => _cancellation != null;

    /// <summary>
    /// Whether a resume is worth attempting. False on a first run, and after a resume was refused.
    /// </summary>
    /// <remarks>
    /// A method rather than a property because GDScript reads it: only <c>[Export]</c>ed properties are
    /// reliably visible across that boundary, while every public method is.
    /// </remarks>
    public bool HasStoredSession()
    {
      return !string.IsNullOrEmpty(RefreshTokenStore.Load());
    }

    /// <summary>
    /// Starts the ceremony. There is deliberately no separate "register" entry point: the page in
    /// the browser offers signing in, creating an account and recovering one, so which of the three
    /// happens is decided there rather than by the button the player pressed in the menu.
    /// </summary>
    public void StartLogin(string loginServerUrl)
    {
      if (IsBusy)
      {
        GD.PushWarning("PasskeyLoginService: a login is already in progress");
        return;
      }

      _cancellation = new CancellationTokenSource(TimeSpan.FromSeconds(TimeoutSeconds));

      // Fire and forget on purpose: the result comes back as a signal on the main thread, which is
      // the only way a Node may talk to the rest of the scene tree.
      _ = RunAsync(loginServerUrl, _cancellation.Token);
    }

    /// <summary>
    /// Tries the stored session. Ends in <see cref="LoginSucceededEventHandler"/>,
    /// <see cref="ResumeUnavailableEventHandler"/> if there is nothing usable to resume, or
    /// <see cref="LoginFailedEventHandler"/> if the login server could not be reached at all.
    /// </summary>
    public void TryResume(string loginServerUrl)
    {
      if (IsBusy)
      {
        GD.PushWarning("PasskeyLoginService: a login is already in progress");
        return;
      }

      // Shorter than the ceremony's budget: nobody is being waited for here, so a resume that has not
      // answered in this long is a server that is not going to.
      _cancellation = new CancellationTokenSource(TimeSpan.FromSeconds(ResumeTimeoutSeconds));

      _ = ResumeAsync(loginServerUrl, _cancellation.Token);
    }

    /// <summary>
    /// Forgets the stored session, here and on the server.
    /// </summary>
    /// <remarks>
    /// The local copy goes first and unconditionally. Whether the server is reachable is not the
    /// player's problem: on a shared machine what they asked for is that the next person cannot start
    /// the game as them, and clearing the file is what delivers that. The revoke call closes the other
    /// half - a token that was copied off this machine earlier stops working too - and its failure is
    /// logged rather than surfaced, because there is nothing to retry against.
    /// </remarks>
    public void SignOut(string loginServerUrl)
    {
      var stored = RefreshTokenStore.Load();

      RefreshTokenStore.Clear();

      if (string.IsNullOrEmpty(stored))
      {
        return;
      }

      _ = RevokeAsync(loginServerUrl, stored);
    }

    public void Cancel()
    {
      _cancellation?.Cancel();
    }

    private static async Task RevokeAsync(string loginServerUrl, string refreshToken)
    {
      try
      {
        using var api = new AuthApiClient(loginServerUrl);
        using var cancellation = new CancellationTokenSource(TimeSpan.FromSeconds(ResumeTimeoutSeconds));

        await api.RevokeAsync(refreshToken, cancellation.Token).ConfigureAwait(false);
      }
      catch (Exception e)
      {
        GD.PushWarning($"PasskeyLoginService: could not revoke the stored session - {e.Message}");
      }
    }

    private async Task ResumeAsync(string loginServerUrl, CancellationToken cancellationToken)
    {
      try
      {
        var stored = RefreshTokenStore.Load();

        if (string.IsNullOrEmpty(stored))
        {
          Emit(SignalName.ResumeUnavailable);
          return;
        }

        using var api = new AuthApiClient(loginServerUrl);

        var tokens = await api.RefreshAsync(stored, cancellationToken).ConfigureAwait(false);

        if (tokens == null)
        {
          // Expired, revoked, or the server saw this token twice. Keeping it would only mean sending
          // a token we know is dead on every future start.
          RefreshTokenStore.Clear();
          Emit(SignalName.ResumeUnavailable);

          return;
        }

        Accept(tokens);
      }
      catch (OperationCanceledException)
      {
        Fail("Could not reach the login server.");
      }
      catch (Exception e)
      {
        // The token is left alone: a network failure says nothing about whether it is still good.
        GD.PushError($"PasskeyLoginService: resume failed - {e}");
        Fail("Could not reach the login server.");
      }
      finally
      {
        ClearCancellation();
      }
    }

    private async Task RunAsync(string loginServerUrl, CancellationToken cancellationToken)
    {
      try
      {
        // Bind before starting the session: the server records the redirect target, so the port has
        // to be known first.
        using var callbackServer = new LoopbackCallbackServer();
        using var api = new AuthApiClient(loginServerUrl);

        var verifier = Pkce.CreateVerifier();
        var state = Pkce.CreateState();

        var session = await api
          .StartAsync(callbackServer.RedirectUri, Pkce.Challenge(verifier), state, cancellationToken)
          .ConfigureAwait(false);

        if (string.IsNullOrEmpty(session?.LoginUrl))
        {
          Fail("The login server did not return a sign-in link.");
          return;
        }

        OpenBrowser(session.LoginUrl);
        Emit(SignalName.AwaitingBrowser);

        var callback = await callbackServer
          .WaitForCallbackAsync(cancellationToken)
          .ConfigureAwait(false);

        if (!string.IsNullOrEmpty(callback.Error))
        {
          Fail("Sign-in was refused in the browser.");
          return;
        }

        // The state check is what tells our own redirect apart from one another local process
        // aimed at this port. Compared before the code is spent, so a forged callback cannot burn
        // the real one.
        if (callback.State != state)
        {
          Fail("The sign-in response did not match this request.");
          return;
        }

        if (string.IsNullOrEmpty(callback.Code))
        {
          Fail("The sign-in response carried no code.");
          return;
        }

        var tokens = await api
          .ExchangeAsync(callback.Code, verifier, cancellationToken)
          .ConfigureAwait(false);

        if (tokens == null)
        {
          Fail("The login server did not return a token.");
          return;
        }

        Accept(tokens);
      }
      catch (OperationCanceledException)
      {
        Fail("Sign-in was cancelled.");
      }
      catch (Exception e)
      {
        // The detail goes to the log; the player is told something they can act on.
        GD.PushError($"PasskeyLoginService: login failed - {e}");
        Fail("Could not reach the login server.");
      }
      finally
      {
        ClearCancellation();
      }
    }

    /// <summary>
    /// Stores the successor before announcing the login. The server retired the token we sent, so the
    /// one in hand is the only one that still works and losing it costs the player a ceremony.
    /// </summary>
    private void Accept(AuthApiClient.Tokens tokens)
    {
      RefreshTokenStore.Save(tokens.RefreshToken);

      Emit(SignalName.LoginSucceeded, tokens.Token);
    }

    /// <summary>
    /// The entire platform-specific surface of passkey login: Godot maps this onto ShellExecute,
    /// xdg-open and NSWorkspace respectively.
    /// </summary>
    private static void OpenBrowser(string url)
    {
      var error = OS.ShellOpen(url);

      if (error != Error.Ok)
      {
        throw new InvalidOperationException($"OS.ShellOpen returned {error}");
      }
    }

    private void Fail(string reason)
    {
      Emit(SignalName.LoginFailed, reason);
    }

    private void ClearCancellation()
    {
      var cancellation = _cancellation;
      _cancellation = null;
      cancellation?.Dispose();
    }

    /// <summary>
    /// Signals must be raised on the main thread; everything above this runs on a task thread.
    /// </summary>
    private void Emit(StringName signal, string argument = null)
    {
      if (argument == null)
      {
        Callable.From(() => EmitSignal(signal)).CallDeferred();
      }
      else
      {
        Callable.From(() => EmitSignal(signal, argument)).CallDeferred();
      }
    }

    public override void _ExitTree()
    {
      Cancel();
    }

    private const int ResumeTimeoutSeconds = 15;
  }
}
