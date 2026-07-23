import { LiveScreenLogOptions, SessionInitResponse, LiveScreenLogUser } from './types';

// Injected at build time; fallback for source
declare const __SDK_VERSION__: string | undefined;
const SDK_VERSION = typeof __SDK_VERSION__ !== 'undefined' ? __SDK_VERSION__ : '1.1.0';
const SDK_NAME = 'livescreenlog-browser';

class LiveScreenLogSDK {
  private projectKey!: string;
  private userId: string = '';
  private endpoint!: string;
  private mode!: 'REPLAY' | 'LOGS' | 'BOTH';
  private onSessionReady?: (sessionId: string) => void;
  private tags: Record<string, string> = {};
  private sdkIntegration: string = 'browser';

  private sessionId: string | null = null;
  private token: string | null = null;
  private stopRecord: any = null;
  private events: any[] = [];
  
  private heartbeatTimer: any = null;
  private flushTimer: any = null;
  private sseConn: EventSource | null = null;
  private isRecordingStarted = false;

  public init(options: LiveScreenLogOptions) {
    const key = options.apiKey || options.projectKey;
    if (!key) {
      this.logError('init() requires apiKey or projectKey');
      return;
    }
    const resolvedId = options.id ?? options.userId;
    if (resolvedId == null || resolvedId === '') {
      this.logError('init() requires id (user identifier)');
      return;
    }
    this.projectKey = key;
    this.userId = String(resolvedId);
    this.endpoint = options.dsn || options.endpoint || window.location.origin;
    this.mode = options.mode || 'BOTH';
    this.onSessionReady = options.onSessionReady;
    this.sdkIntegration = options.integration || 'browser';
    if (options.tags && typeof options.tags === 'object') {
      this.tags = { ...this.tags, ...this.normalizeTags(options.tags) };
    }

    this.logInfo('Initializing LiveScreenLog SDK', SDK_NAME + '@' + SDK_VERSION, 'Mode:', this.mode);

    // Defer so setUser/setTag/setTags right after init() still apply before handshake
    const begin = () => {
      if (this.mode === 'REPLAY' || this.mode === 'BOTH') {
        this.ensureRrweb(() => this.startWorkflow());
      } else {
        this.startWorkflow();
      }
    };
    if (typeof queueMicrotask === 'function') {
      queueMicrotask(begin);
    } else {
      setTimeout(begin, 0);
    }
  }

  /** Set user identity — accepts string userId or { id } / { userId } */
  public setUser(user: LiveScreenLogUser | null | undefined) {
    if (user == null) {
      this.userId = '';
      return;
    }
    if (typeof user === 'string' || typeof user === 'number') {
      this.userId = String(user);
      return;
    }
    const id = user.id ?? user.userId;
    this.userId = id != null ? String(id) : '';
  }

  public setTag(key: string, value: string | number | boolean | null | undefined) {
    if (!key) return;
    if (value == null) {
      delete this.tags[key];
      return;
    }
    this.tags[key] = String(value);
  }

  public setTags(tags: Record<string, string | number | boolean | null | undefined> | null | undefined) {
    if (!tags || typeof tags !== 'object') return;
    const normalized = this.normalizeTags(tags);
    this.tags = { ...this.tags, ...normalized };
  }

  private normalizeTags(
    tags: Record<string, string | number | boolean | null | undefined>
  ): Record<string, string> {
    const out: Record<string, string> = {};
    for (const [k, v] of Object.entries(tags)) {
      if (!k || v == null) continue;
      out[k] = String(v);
    }
    return out;
  }

  private startWorkflow() {
    // Dialogs needed for REPLAY/BOTH (visual capture) and LOGS/BOTH (custom events)
    if (this.mode === 'REPLAY' || this.mode === 'BOTH' || this.mode === 'LOGS') {
      this.hookDialogs();
    }
    if (this.mode === 'LOGS' || this.mode === 'BOTH') {
      this.hookLogs();
    }

    // Discover configuration by hitting /api/sessions
    this.requestSessionInit(null)
      .then((data) => {
        this.logInfo('Server handshake response:', data);
        
        if (data.enabled && data.sessionId && data.token) {
          // Immediately active (ALL mode or matched A mode)
          this.beginSessionRecording(data.sessionId, data.token);
        } else if (data.recordingMode === 'B') {
          // B Mode: remote standby (SSE)
          this.setupModeB();
        } else if (data.recordingMode === 'C') {
          // C Mode: auto trigger on error
          this.setupModeC();
        } else {
          this.logInfo('Recording disabled for this session. Policy mode:', data.recordingMode);
        }
      })
      .catch((err) => {
        this.logError('Failed to initialize session from server:', err);
      });
  }

  private requestSessionInit(trigger: 'FORCE' | 'ERROR' | null): Promise<SessionInitResponse> {
    const body: Record<string, unknown> = {
      projectKey: this.projectKey,
      apiKey: this.projectKey,
      userId: this.userId || undefined,
      distinctId: navigator.userAgent.substring(0, 128),
      source: window.location.pathname,
      trigger: trigger || undefined,
      tags: Object.keys(this.tags).length > 0 ? this.tags : undefined,
      sdkName: SDK_NAME,
      sdkVersion: SDK_VERSION,
      sdkIntegration: this.sdkIntegration,
    };

    return fetch(`${this.endpoint}/api/sessions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    }).then((r) => {
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      return r.json();
    });
  }

  private beginSessionRecording(sessionId: string, token: string) {
    if (this.isRecordingStarted) return;
    this.sessionId = sessionId;
    this.token = token;
    this.isRecordingStarted = true;

    this.logInfo(`Session activated. Recording started. ID: ${sessionId}`);

    if (this.onSessionReady) {
      try {
        this.onSessionReady(sessionId);
      } catch (e) {
        this.logError('Error inside onSessionReady callback:', e);
      }
    }

    // Flush any buffered startup logs or pre-trigger events
    const initialBatch = this.events.splice(0);
    if (initialBatch.length > 0) {
      this.sendEvents(initialBatch);
    }

    // Start rrweb recorder if replay mode is active
    if ((this.mode === 'REPLAY' || this.mode === 'BOTH') && window.rrweb) {
      if (this.stopRecord) {
        this.stopRecord();
        this.stopRecord = null;
      }
      this.stopRecord = window.rrweb.record({
        emit: (event: any) => {
          this.events.push(event);
          if (this.events.length >= 10) this.flush();
        },
        maskAllInputs: true,
        blockClass: 'livescreenlog-block',
        ignoreClass: 'livescreenlog-ignore',
        maskInputOptions: {
          password: true,
          email: true,
          tel: true,
        },
      });
    }

    // Start timers
    this.flushTimer = setInterval(() => this.flush(), 3000);
    
    this.heartbeatTimer = setInterval(() => {
      if (this.token) {
        fetch(`${this.endpoint}/api/heartbeat`, {
          method: 'POST',
          headers: { 'x-livescreenlog-session-token': this.token }
        }).catch(() => {});
      }
    }, 15000);

    // Unload listeners
    window.addEventListener('beforeunload', () => {
      this.flush();
      if (this.token) {
        fetch(`${this.endpoint}/api/stop`, {
          method: 'POST',
          headers: { 'x-livescreenlog-session-token': this.token },
          keepalive: true
        }).catch(() => {});
      }
    });
  }

  private setupModeB() {
    this.logInfo('Standby mode (B): waiting for remote trigger...');
    this.startPreTriggerBuffer();

    const url = `${this.endpoint}/api/push/connect?projectKey=${encodeURIComponent(this.projectKey)}&userId=${encodeURIComponent(this.userId)}`;
    this.sseConn = new EventSource(url);

    this.sseConn.addEventListener('INIT', (e) => {
      this.logInfo('SSE connected to standby channel:', e.data);
    });

    this.sseConn.addEventListener('START_RECORDING', () => {
      this.logInfo('START_RECORDING push event received from admin. Activating session...');
      if (this.sseConn) {
        this.sseConn.close();
        this.sseConn = null;
      }
      this.requestSessionInit('FORCE')
        .then((data) => {
          if (data.enabled && data.sessionId && data.token) {
            this.beginSessionRecording(data.sessionId, data.token);
          } else {
            this.logError('Server rejected FORCE trigger activation');
          }
        })
        .catch((err) => this.logError('Failed to trigger session activation:', err));
    });

    this.sseConn.onerror = () => {
      if (!this.isRecordingStarted && this.sseConn) {
        this.logInfo('SSE connection disconnected. Re-connecting in 10s...');
        this.sseConn.close();
        this.sseConn = null;
        setTimeout(() => this.setupModeB(), 10000);
      }
    };
  }

  private setupModeC() {
    this.logInfo('Standby mode (C): waiting for page error...');
    this.startPreTriggerBuffer();

    const triggerOnError = () => {
      if (this.isRecordingStarted) return;
      this.logInfo('Error detected in Mode C. Triggering session...');
      
      this.requestSessionInit('ERROR')
        .then((data) => {
          if (data.enabled && data.sessionId && data.token) {
            this.beginSessionRecording(data.sessionId, data.token);
          }
        })
        .catch((err) => this.logError('Failed to initialize session on error trigger:', err));
    };

    window.addEventListener('error', triggerOnError);
    window.addEventListener('unhandledrejection', triggerOnError);
  }

  private startPreTriggerBuffer() {
    // If Replay is active, start a silent sliding window ring-buffer (last 300 events)
    if ((this.mode === 'REPLAY' || this.mode === 'BOTH') && window.rrweb) {
      if (this.stopRecord) this.stopRecord();
      this.stopRecord = window.rrweb.record({
        emit: (event: any) => {
          this.events.push(event);
          if (this.events.length > 300) {
            this.events.shift();
          }
        }
      });
    }
  }

  private flush() {
    if (this.events.length === 0 || !this.token) return;
    const batch = this.events.splice(0);
    void this.sendEvents(batch);
  }

  /**
   * Upload event batch. Gzip when batch is large enough and CompressionStream is available
   * (reduces uplink; client CPU cost only on larger flushes). Falls back to plain JSON.
   */
  private async sendEvents(batch: any[]) {
    if (!this.token || !batch || batch.length === 0) return;

    const json = JSON.stringify(batch);
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'x-livescreenlog-session-token': this.token,
    };

    // Skip gzip for tiny payloads — overhead can outweigh savings.
    const shouldTryGzip = batch.length >= 30 || json.length >= 2048;
    let body: BodyInit = json;

    if (shouldTryGzip && typeof CompressionStream !== 'undefined') {
      try {
        const stream = new Blob([json]).stream().pipeThrough(new CompressionStream('gzip'));
        const compressed = await new Response(stream).arrayBuffer();
        if (compressed.byteLength > 0 && compressed.byteLength < json.length) {
          headers['Content-Encoding'] = 'gzip';
          body = compressed;
        }
      } catch {
        body = json;
        delete headers['Content-Encoding'];
      }
    }

    fetch(`${this.endpoint}/api/events`, {
      method: 'POST',
      headers,
      body,
    }).catch(() => {});
  }

  // --- Dynamic Script Loader ---
  private ensureRrweb(callback: () => void) {
    if (window.rrweb) {
      callback();
      return;
    }
    this.logInfo('rrweb not found on page. Dynamically injecting script...');
    const script = document.createElement('script');
    script.src = 'https://cdn.jsdelivr.net/npm/rrweb@1.1.3/dist/rrweb.min.js';
    script.async = true;
    script.onload = () => {
      this.logInfo('rrweb script injected and loaded successfully.');
      callback();
    };
    script.onerror = () => {
      this.logError('Failed to load rrweb script from CDN. Replay features might not work.');
      callback(); // Proceed anyway, logs mode might still work
    };
    document.head.appendChild(script);
  }

  // --- Logs & Intercept modules ---
  private isLiveScreenLogUrl(url: string): boolean {
    if (!url) return false;
    try {
      const endpointHost = new URL(this.endpoint, window.location.origin).origin;
      const resolved = new URL(url, window.location.origin);
      if (resolved.origin === endpointHost) {
        const path = resolved.pathname;
        if (
          path.includes('/api/events') ||
          path.includes('/api/heartbeat') ||
          path.includes('/api/sessions') ||
          path.includes('/api/stop') ||
          path.includes('/api/push/')
        ) {
          return true;
        }
      }
    } catch {}
    return (
      url.includes(this.endpoint) ||
      url.includes('/api/events') ||
      url.includes('/api/heartbeat') ||
      url.includes('/api/sessions') ||
      url.includes('/api/stop')
    );
  }

  private takeRrwebSnapshot() {
    try {
      window.rrweb?.record?.takeFullSnapshot?.();
    } catch {}
  }

  private dialogQueue: Promise<unknown> = Promise.resolve();

  private enqueueDialog<T>(run: () => Promise<T>): Promise<T> {
    const next = this.dialogQueue.then(run, run);
    this.dialogQueue = next.then(
      () => undefined,
      () => undefined
    );
    return next;
  }

  private dismissDialogOverlay(overlay: HTMLElement | null) {
    if (overlay && document.body.contains(overlay)) {
      document.body.removeChild(overlay);
    }
    this.takeRrwebSnapshot();
    if (this.token) this.flush();
  }

  /**
   * Chrome desktop-style system dialog (DOM). Fully replaces native alert/confirm/prompt
   * so rrweb can record the UI. Interactive; does not call native dialogs.
   */
  private openNativeLikeDialog(
    kind: 'alert' | 'confirm' | 'prompt',
    message: string,
    defaultValue?: string
  ): Promise<{ ok: boolean; value: string | null }> {
    this.ensureAlertStyles();

    return new Promise((resolve) => {
      const hostLabel = window.location.host || 'This page';
      const overlay = document.createElement('div');
      overlay.className = 'livescreenlog-dialog-overlay';
      overlay.setAttribute('data-livescreenlog-dialog', kind);
      overlay.setAttribute('role', 'alertdialog');
      overlay.setAttribute('aria-modal', 'true');
      overlay.setAttribute('aria-labelledby', 'livescreenlog-dialog-title');
      overlay.setAttribute('aria-describedby', 'livescreenlog-dialog-body');

      const modal = document.createElement('div');
      modal.className = 'livescreenlog-dialog-modal';

      // Basic browser-style: hostname as title (Chrome/macOS pattern)
      const title = document.createElement('div');
      title.id = 'livescreenlog-dialog-title';
      title.className = 'livescreenlog-dialog-title';
      title.textContent = hostLabel + ' says';

      const body = document.createElement('div');
      body.id = 'livescreenlog-dialog-body';
      body.className = 'livescreenlog-dialog-body';
      body.textContent = message;

      modal.appendChild(title);
      modal.appendChild(body);

      let input: HTMLInputElement | null = null;
      if (kind === 'prompt') {
        input = document.createElement('input');
        input.type = 'text';
        input.className = 'livescreenlog-dialog-input';
        input.value = defaultValue ?? '';
        input.setAttribute('aria-label', 'Prompt');
        input.spellcheck = false;
        modal.appendChild(input);
      }

      const footer = document.createElement('div');
      footer.className = 'livescreenlog-dialog-footer';

      let settled = false;
      const finish = (ok: boolean, value: string | null) => {
        if (settled) return;
        settled = true;
        document.removeEventListener('keydown', onKey, true);
        this.dismissDialogOverlay(overlay);
        resolve({ ok, value });
      };

      const onKey = (e: KeyboardEvent) => {
        if (e.key === 'Escape') {
          if (kind === 'alert') return;
          e.preventDefault();
          e.stopPropagation();
          finish(false, null);
        } else if (e.key === 'Enter') {
          if (kind === 'prompt' && document.activeElement === input) {
            e.preventDefault();
            e.stopPropagation();
            finish(true, input ? input.value : '');
          } else if (kind !== 'prompt') {
            e.preventDefault();
            e.stopPropagation();
            finish(true, null);
          }
        }
      };

      if (kind === 'confirm' || kind === 'prompt') {
        const cancelBtn = document.createElement('button');
        cancelBtn.type = 'button';
        cancelBtn.className = 'livescreenlog-dialog-btn livescreenlog-dialog-btn-secondary';
        cancelBtn.textContent = 'Cancel';
        cancelBtn.addEventListener('click', (e) => {
          e.preventDefault();
          e.stopPropagation();
          finish(false, null);
        });
        footer.appendChild(cancelBtn);
      }

      const okBtn = document.createElement('button');
      okBtn.type = 'button';
      okBtn.className = 'livescreenlog-dialog-btn livescreenlog-dialog-btn-primary';
      okBtn.textContent = 'OK';
      okBtn.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        if (kind === 'prompt') {
          finish(true, input ? input.value : '');
        } else {
          finish(true, null);
        }
      });
      footer.appendChild(okBtn);

      modal.appendChild(footer);
      overlay.appendChild(modal);

      // Block page interaction behind the dimmer (native-like)
      overlay.addEventListener('mousedown', (e) => {
        if (e.target === overlay) {
          e.preventDefault();
          e.stopPropagation();
        }
      });

      document.body.appendChild(overlay);
      void overlay.offsetHeight;
      this.takeRrwebSnapshot();
      if (this.token) this.flush();

      document.addEventListener('keydown', onKey, true);
      requestAnimationFrame(() => {
        if (input) {
          input.focus();
          input.select();
        } else {
          okBtn.focus();
        }
      });
    });
  }

  /**
   * Browser cannot run interactive UI while synchronously blocking the main thread.
   * We mirror native *look/feel* with a DOM dialog and resolve via a thenable return
   * for confirm/prompt when the caller treats the result as a Promise (async/await).
   * Classic `if (confirm())` is supported via a short microtask barrier only when
   * the dialog was already answered in a prior turn — otherwise we open the dialog
   * and return a boolean Thenable that works with await and .then().
   */
  private hookDialogs() {
    this.ensureAlertStyles();

    window.alert = ((message?: any) => {
      const msgStr = message === undefined || message === null ? '' : String(message);
      const p = this.enqueueDialog(async () => {
        this.captureCustomLog('ALERT', { message: msgStr, phase: 'open' });
        if (this.token) this.flush();
        await this.openNativeLikeDialog('alert', msgStr);
        this.captureCustomLog('ALERT', { message: msgStr, phase: 'close' });
        if (this.token) this.flush();
      });
      // Thenable so callers can `await alert(...)` / `.then()` after OK
      const wrapper = {
        then: (onfulfilled?: any, onrejected?: any) => p.then(onfulfilled, onrejected),
      };
      return wrapper as unknown as void;
    }) as typeof window.alert;

    const makeThenableBool = (p: Promise<boolean>): boolean & PromiseLike<boolean> => {
      const wrapper = Object.create(Boolean.prototype) as boolean & PromiseLike<boolean>;
      // default coerced value while pending (native would block; we cannot)
      let settled = false;
      let value = false;
      p.then((v) => {
        settled = true;
        value = v;
      });
      Object.defineProperty(wrapper, 'valueOf', {
        value() {
          return settled ? value : false;
        },
      });
      Object.defineProperty(wrapper, 'toString', {
        value() {
          return String(settled ? value : false);
        },
      });
      wrapper.then = (onfulfilled, onrejected) => p.then(onfulfilled, onrejected);
      return wrapper;
    };

    window.confirm = (message?: string) => {
      const msgStr = message ? String(message) : '';
      const resultPromise = this.enqueueDialog(async () => {
        this.captureCustomLog('CONFIRM', { message: msgStr, phase: 'open' });
        if (this.token) this.flush();
        const { ok } = await this.openNativeLikeDialog('confirm', msgStr);
        this.captureCustomLog('CONFIRM', { message: msgStr, phase: 'close', result: ok });
        if (this.token) this.flush();
        return ok;
      });
      return makeThenableBool(resultPromise) as unknown as boolean;
    };

    window.prompt = (message?: string, defaultValue?: string) => {
      const msgStr = message ? String(message) : '';
      const def = defaultValue !== undefined ? String(defaultValue) : '';
      const resultPromise = this.enqueueDialog(async () => {
        this.captureCustomLog('PROMPT', {
          message: msgStr,
          defaultValue: def,
          phase: 'open',
        });
        if (this.token) this.flush();
        const { ok, value } = await this.openNativeLikeDialog('prompt', msgStr, def);
        const out = ok ? value : null;
        this.captureCustomLog('PROMPT', {
          message: msgStr,
          defaultValue: def,
          phase: 'close',
          result: out,
        });
        if (this.token) this.flush();
        return out;
      });

      // Thenable string|null for await; sync coercion yields null until settled
      const wrapper = {
        then: (
          onfulfilled?: ((v: string | null) => any) | null,
          onrejected?: ((e: any) => any) | null
        ) => resultPromise.then(onfulfilled!, onrejected!),
        valueOf: () => null as string | null,
        toString: () => '',
      };
      return wrapper as unknown as string | null;
    };
  }

  private hookLogs() {
    const oldError = window.onerror;
    window.onerror = (message, source, lineno, colno, error) => {
      this.captureCustomLog('ERROR', {
        message: String(message),
        source: String(source),
        lineno,
        colno,
        stack: error ? error.stack : null
      });
      if (oldError) {
        return oldError(message, source, lineno, colno, error);
      }
      return false;
    };

    window.addEventListener('unhandledrejection', (event) => {
      this.captureCustomLog('UNHANDLED_REJECTION', {
        reason: event.reason ? String(event.reason.message || event.reason) : 'Unknown reason',
        stack: event.reason && event.reason.stack ? event.reason.stack : null
      });
    });

    const oldConsoleError = console.error;
    console.error = (...args: any[]) => {
      this.captureCustomLog('CONSOLE_ERROR', {
        args: args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a))
      });
      oldConsoleError.apply(console, args);
    };

    const oldConsoleWarn = console.warn;
    console.warn = (...args: any[]) => {
      this.captureCustomLog('CONSOLE_WARN', {
        args: args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a))
      });
      oldConsoleWarn.apply(console, args);
    };

    const oldFetch = window.fetch;
    window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      const startTime = Date.now();
      const url = typeof input === 'string' ? input : (input instanceof URL ? input.href : input.url);
      const method = (init && init.method) || (typeof input !== 'string' && !(input instanceof URL) ? input.method : 'GET') || 'GET';
      const skipLog = this.isLiveScreenLogUrl(url);

      try {
        const response = await oldFetch(input, init);
        if (!skipLog) {
          const duration = Date.now() - startTime;
          this.captureCustomLog('FETCH_RESPONSE', {
            url,
            method,
            status: response.status,
            statusText: response.statusText,
            durationMs: duration
          });
        }
        return response;
      } catch (err: any) {
        if (!skipLog) {
          const duration = Date.now() - startTime;
          this.captureCustomLog('FETCH_ERROR', {
            url,
            method,
            error: err.message || String(err),
            durationMs: duration
          });
        }
        throw err;
      }
    };
  }

  private ensureAlertStyles() {
    if (document.getElementById('livescreenlog-alert-styles')) return;
    const style = document.createElement('style');
    style.id = 'livescreenlog-alert-styles';
    style.textContent = `
      @keyframes livescreenlog-dialog-in {
        from { transform: scale(0.96) translateY(6px); opacity: 0; }
        to { transform: scale(1) translateY(0); opacity: 1; }
      }
      .livescreenlog-dialog-overlay {
        position: fixed !important;
        inset: 0 !important;
        z-index: 2147483647 !important;
        display: flex !important;
        align-items: center !important;
        justify-content: center !important;
        background: rgba(0, 0, 0, 0.32) !important;
        font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif !important;
        -webkit-font-smoothing: antialiased !important;
      }
      .livescreenlog-dialog-modal {
        background: #ffffff !important;
        color: #202124 !important;
        border-radius: 8px !important;
        box-shadow:
          0 1px 2px rgba(60, 64, 67, 0.3),
          0 2px 6px 2px rgba(60, 64, 67, 0.15) !important;
        width: 344px !important;
        max-width: calc(100vw - 32px) !important;
        padding: 20px 20px 16px !important;
        box-sizing: border-box !important;
        animation: livescreenlog-dialog-in 0.14s ease-out !important;
        text-align: left !important;
      }
      .livescreenlog-dialog-title {
        font-size: 15px !important;
        font-weight: 400 !important;
        line-height: 1.4 !important;
        color: #202124 !important;
        margin: 0 0 10px !important;
        overflow: hidden !important;
        text-overflow: ellipsis !important;
        white-space: nowrap !important;
      }
      .livescreenlog-dialog-body {
        font-size: 13px !important;
        font-weight: 400 !important;
        line-height: 1.5 !important;
        color: #3c4043 !important;
        white-space: pre-wrap !important;
        word-break: break-word !important;
        margin: 0 0 4px !important;
        max-height: 40vh !important;
        overflow: auto !important;
      }
      .livescreenlog-dialog-input {
        display: block !important;
        width: 100% !important;
        box-sizing: border-box !important;
        margin: 12px 0 0 !important;
        padding: 8px 10px !important;
        border: 1px solid #dadce0 !important;
        border-radius: 4px !important;
        font-size: 13px !important;
        line-height: 1.4 !important;
        color: #202124 !important;
        background: #fff !important;
        outline: none !important;
        font-family: inherit !important;
      }
      .livescreenlog-dialog-input:focus {
        border-color: #1a73e8 !important;
        box-shadow: 0 0 0 1px #1a73e8 !important;
      }
      .livescreenlog-dialog-footer {
        display: flex !important;
        justify-content: flex-end !important;
        align-items: center !important;
        gap: 8px !important;
        margin-top: 18px !important;
      }
      .livescreenlog-dialog-btn {
        appearance: none !important;
        border-radius: 4px !important;
        font-size: 13px !important;
        font-weight: 500 !important;
        line-height: 1 !important;
        padding: 8px 16px !important;
        cursor: pointer !important;
        font-family: inherit !important;
        min-width: 64px !important;
        box-sizing: border-box !important;
      }
      .livescreenlog-dialog-btn-primary {
        background: #1a73e8 !important;
        color: #ffffff !important;
        border: 1px solid transparent !important;
      }
      .livescreenlog-dialog-btn-primary:hover {
        background: #1765cc !important;
      }
      .livescreenlog-dialog-btn-primary:focus-visible {
        outline: 2px solid #1a73e8 !important;
        outline-offset: 2px !important;
      }
      .livescreenlog-dialog-btn-secondary {
        background: #ffffff !important;
        color: #1a73e8 !important;
        border: 1px solid #dadce0 !important;
      }
      .livescreenlog-dialog-btn-secondary:hover {
        background: #f8f9fa !important;
      }
      .livescreenlog-dialog-btn-secondary:focus-visible {
        outline: 2px solid #1a73e8 !important;
        outline-offset: 2px !important;
      }
    `;
    document.head.appendChild(style);
  }

  private captureCustomLog(logType: string, payload: any) {
    const customEvent = {
      type: 5,
      data: {
        tag: logType,
        payload
      },
      timestamp: Date.now()
    };

    this.events.push(customEvent);

    if (this.token && this.events.length >= 10) {
      this.flush();
    }
  }

  // --- Log helpers ---
  private logInfo(msg: string, ...args: any[]) {
    console.log(`%c[LiveScreenLog]%c ${msg}`, 'color: #6366f1; font-weight: bold;', '', ...args);
  }

  private logError(msg: string, ...args: any[]) {
    console.error(`%c[LiveScreenLog]%c ${msg}`, 'color: #ef4444; font-weight: bold;', '', ...args);
  }
}

// Export singleton instance
export const LiveScreenLog = new LiveScreenLogSDK();
export { SDK_VERSION, SDK_NAME };
export default LiveScreenLog;

// Attach to window object for global UMD support (<script> tags)
if (typeof window !== 'undefined') {
  window.LiveScreenLog = LiveScreenLog;
}
