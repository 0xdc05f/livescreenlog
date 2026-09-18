export interface LiveScreenLogOptions {
  /** Project API key (preferred alias: apiKey) */
  apiKey?: string;
  /** @deprecated use apiKey */
  projectKey?: string;
  /** DSN / base URL alias for endpoint */
  dsn?: string;
  endpoint?: string;
  /**
   * Preferred user identifier (employee id / user id).
   * Can be omitted at init and set later via setUser().
   */
  id?: string | number;
  /** @deprecated use id */
  userId?: string | number;
  /** Initial tags */
  tags?: Record<string, string>;
  /** SDK integration label: browser | vue | react | etc */
  integration?: string;
  mode?: 'REPLAY' | 'LOGS' | 'BOTH';
  onSessionReady?: (sessionId: string) => void;
  onInitError?: (error: Error) => void;
  onStandby?: (mode: string) => void;
}

export type LiveScreenLogUser =
  | string
  | {
      id?: string | number;
      userId?: string | number;
      [key: string]: unknown;
    };

export interface SessionInitResponse {
  sessionId: string | null;
  token: string | null;
  enabled: boolean;
  recordingMode: string;
}

declare global {
  interface Window {
    rrweb?: any;
    LiveScreenLog?: any;
  }
}
