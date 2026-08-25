'use strict';

// The browser is the WebAuthn client. Everything platform-specific - Windows Hello, Touch ID,
// iCloud Keychain, a YubiKey, a phone over QR - happens inside navigator.credentials, which is why
// the game itself needs no per-OS code.

(function () {
  const app = document.getElementById('app');
  const sessionId = app.dataset.session;
  const startInRegisterMode = app.dataset.register === 'true';

  const panels = {
    actions: document.getElementById('actions'),
    register: document.getElementById('register-form'),
    recover: document.getElementById('recover-form'),
    recovery: document.getElementById('recovery'),
    done: document.getElementById('done')
  };

  const recoveryList = document.getElementById('recovery-codes');
  const statusLine = document.getElementById('status');
  const errorLine = document.getElementById('error');

  function show(element) {
    element.classList.remove('hidden');
  }

  function hide(element) {
    element.classList.add('hidden');
  }

  /** Only ever one panel at a time; the recovery-code list is additive on top of "done". */
  function showOnly() {
    const wanted = new Set(arguments);

    for (const key of Object.keys(panels)) {
      if (wanted.has(key)) {
        show(panels[key]);
      } else {
        hide(panels[key]);
      }
    }
  }

  function setStatus(text) {
    statusLine.textContent = text;
  }

  function setError(text) {
    errorLine.textContent = text;
    show(errorLine);
  }

  function clearError() {
    errorLine.textContent = '';
    hide(errorLine);
  }

  function setBusy(busy) {
    for (const button of document.querySelectorAll('button')) {
      button.disabled = busy;
    }
  }

  async function post(path, body) {
    const response = await fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      credentials: 'omit'
    });

    const payload = await response.json().catch(function () {
      return {};
    });

    if (!response.ok) {
      throw new Error(payload.error || 'Request failed');
    }

    return payload;
  }

  // --- base64url plumbing -----------------------------------------------------------------
  // parseCreationOptionsFromJSON / parseRequestOptionsFromJSON / toJSON reached Baseline in March
  // 2025 and do all of this natively. The manual paths below are the fallback for a browser that
  // predates them; they are the same transformation, written out.

  function b64uToBuffer(value) {
    const padded = value.replace(/-/g, '+').replace(/_/g, '/');
    const raw = atob(padded + '='.repeat((4 - (padded.length % 4)) % 4));
    const bytes = new Uint8Array(raw.length);

    for (let i = 0; i < raw.length; i++) {
      bytes[i] = raw.charCodeAt(i);
    }

    return bytes.buffer;
  }

  function bufferToB64u(buffer) {
    const bytes = new Uint8Array(buffer);
    let binary = '';

    for (let i = 0; i < bytes.length; i++) {
      binary += String.fromCharCode(bytes[i]);
    }

    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }

  function decodeDescriptors(descriptors) {
    return (descriptors || []).map(function (descriptor) {
      return Object.assign({}, descriptor, { id: b64uToBuffer(descriptor.id) });
    });
  }

  function parseCreationOptions(json) {
    if (window.PublicKeyCredential && PublicKeyCredential.parseCreationOptionsFromJSON) {
      return PublicKeyCredential.parseCreationOptionsFromJSON(json);
    }

    return Object.assign({}, json, {
      challenge: b64uToBuffer(json.challenge),
      user: Object.assign({}, json.user, { id: b64uToBuffer(json.user.id) }),
      excludeCredentials: decodeDescriptors(json.excludeCredentials)
    });
  }

  function parseRequestOptions(json) {
    if (window.PublicKeyCredential && PublicKeyCredential.parseRequestOptionsFromJSON) {
      return PublicKeyCredential.parseRequestOptionsFromJSON(json);
    }

    return Object.assign({}, json, {
      challenge: b64uToBuffer(json.challenge),
      allowCredentials: decodeDescriptors(json.allowCredentials)
    });
  }

  function serializeRegistration(credential) {
    if (typeof credential.toJSON === 'function') {
      return credential.toJSON();
    }

    return {
      id: credential.id,
      type: credential.type,
      rawId: bufferToB64u(credential.rawId),
      authenticatorAttachment: credential.authenticatorAttachment,
      response: {
        clientDataJSON: bufferToB64u(credential.response.clientDataJSON),
        attestationObject: bufferToB64u(credential.response.attestationObject),
        transports: credential.response.getTransports ? credential.response.getTransports() : []
      },
      clientExtensionResults: credential.getClientExtensionResults()
    };
  }

  function serializeAssertion(credential) {
    if (typeof credential.toJSON === 'function') {
      return credential.toJSON();
    }

    return {
      id: credential.id,
      type: credential.type,
      rawId: bufferToB64u(credential.rawId),
      authenticatorAttachment: credential.authenticatorAttachment,
      response: {
        clientDataJSON: bufferToB64u(credential.response.clientDataJSON),
        authenticatorData: bufferToB64u(credential.response.authenticatorData),
        signature: bufferToB64u(credential.response.signature),
        userHandle: credential.response.userHandle ? bufferToB64u(credential.response.userHandle) : null
      },
      clientExtensionResults: credential.getClientExtensionResults()
    };
  }

  // --- ceremonies -------------------------------------------------------------------------

  async function login() {
    clearError();
    setBusy(true);
    setStatus('Waiting for your passkey...');

    try {
      const options = await post('/api/v1/webauthn/assert/options', { session_id: sessionId });

      // allowCredentials arrives empty. That is what makes the browser offer its own account
      // picker, and what puts "use a phone or tablet" on the list when no local passkey matches.
      const assertion = await navigator.credentials.get({
        publicKey: parseRequestOptions(options.public_key)
      });

      setStatus('Signing you in...');

      await post('/api/v1/webauthn/assert/verify', {
        ceremony_id: options.ceremony_id,
        credential: serializeAssertion(assertion)
      });

      signedIn('You are signed in', []);
    } catch (e) {
      handleCeremonyError(e, 'Sign-in failed. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  async function createCredential(optionsPath, optionsBody, verifyPath, heading) {
    const options = await post(optionsPath, optionsBody);

    const credential = await navigator.credentials.create({
      publicKey: parseCreationOptions(options.public_key)
    });

    setStatus('Finishing up...');

    const result = await post(verifyPath, {
      ceremony_id: options.ceremony_id,
      credential: serializeRegistration(credential)
    });

    signedIn(heading, result.recovery_codes || []);
  }

  async function register(displayName) {
    clearError();
    setBusy(true);
    setStatus('Creating your passkey...');

    try {
      await createCredential(
        '/api/v1/webauthn/register/options',
        { session_id: sessionId, display_name: displayName },
        '/api/v1/webauthn/register/verify',
        'Your account is ready'
      );
    } catch (e) {
      handleCeremonyError(e, 'That name may already be taken, or the passkey was not created.');
    } finally {
      setBusy(false);
    }
  }

  async function recover(displayName, recoveryCode) {
    clearError();
    setBusy(true);
    setStatus('Checking your recovery code...');

    try {
      await createCredential(
        '/api/v1/webauthn/recover/options',
        { session_id: sessionId, display_name: displayName, recovery_code: recoveryCode },
        '/api/v1/webauthn/register/verify',
        'Your account has been recovered'
      );
    } catch (e) {
      handleCeremonyError(e, 'That name and recovery code did not match an account.');
    } finally {
      setBusy(false);
    }
  }

  async function addCredential() {
    clearError();
    setBusy(true);
    setStatus('Creating another passkey...');

    try {
      await createCredential(
        '/api/v1/webauthn/credentials/options',
        { session_id: sessionId },
        '/api/v1/webauthn/credentials/verify',
        'Passkey added'
      );
      setStatus('The new passkey is saved to your account.');
    } catch (e) {
      handleCeremonyError(e, 'The passkey could not be added.');
    } finally {
      setBusy(false);
    }
  }

  /**
   * Authenticated, but not yet returned to the game. The authorization code is only minted when the
   * player presses continue, because it lives a minute and reading a page of recovery codes takes
   * longer than that.
   */
  function signedIn(heading, recoveryCodes) {
    document.getElementById('done-heading').textContent = heading;
    setStatus('');

    if (recoveryCodes.length > 0) {
      recoveryList.replaceChildren();

      for (const code of recoveryCodes) {
        const item = document.createElement('li');
        item.textContent = code;
        recoveryList.appendChild(item);
      }

      showOnly('recovery', 'done');
    } else {
      showOnly('done');
    }
  }

  async function returnToGame() {
    clearError();
    setBusy(true);
    setStatus('Returning to the game...');

    try {
      const result = await post('/api/v1/auth/session/complete', { session_id: sessionId });

      // A plain http navigation to a loopback address. No browser gates this the way they gate a
      // custom scheme, so no extra click is needed.
      window.location.href = result.redirect_to;
    } catch (e) {
      setBusy(false);
      setStatus('');
      setError('This sign-in has expired. Start again from the game.');
    }
  }

  function handleCeremonyError(e, fallback) {
    setStatus('');

    // NotAllowedError is what the browser reports both for a cancelled prompt and for a timeout,
    // and neither is worth alarming anyone about.
    if (e && e.name === 'NotAllowedError') {
      setError('No passkey was used. You can try again.');
      return;
    }

    if (e && e.name === 'InvalidStateError') {
      setError('This device already has a passkey for that account. Try signing in instead.');
      return;
    }

    if (e && e.message === 'unknown_credential') {
      setError("This passkey isn't registered on this server. Create an account or use recovery instead.");
      return;
    }

    setError(fallback);
  }

  // --- wiring -----------------------------------------------------------------------------

  if (!window.PublicKeyCredential) {
    hide(panels.actions);
    setError(
      'This browser cannot use passkeys. Try a current version of Chrome, Edge, Firefox or Safari.'
    );
    return;
  }

  document.getElementById('login-button').addEventListener('click', login);

  document.getElementById('show-register').addEventListener('click', function () {
    clearError();
    showOnly('register');
    document.getElementById('display-name').focus();
  });

  document.getElementById('show-recover').addEventListener('click', function () {
    clearError();
    showOnly('recover');
    document.getElementById('recover-name').focus();
  });

  for (const button of document.querySelectorAll('[data-back]')) {
    button.addEventListener('click', function () {
      clearError();
      showOnly('actions');
    });
  }

  panels.register.addEventListener('submit', function (event) {
    event.preventDefault();
    const name = document.getElementById('display-name').value.trim();

    if (name.length < 3) {
      setError('Pick an account name of at least three characters.');
      return;
    }

    register(name);
  });

  panels.recover.addEventListener('submit', function (event) {
    event.preventDefault();
    const name = document.getElementById('recover-name').value.trim();
    const code = document.getElementById('recover-code').value.trim();

    if (name.length < 3 || code.length < 8) {
      setError('Enter both your account name and a full recovery code.');
      return;
    }

    recover(name, code);
  });

  document.getElementById('continue-button').addEventListener('click', returnToGame);
  document.getElementById('add-credential-button').addEventListener('click', addCredential);

  if (startInRegisterMode) {
    showOnly('register');
    document.getElementById('display-name').focus();
  } else {
    showOnly('actions');
  }
})();
