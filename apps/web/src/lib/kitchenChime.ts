/**
 * The chime of the kitchen (spec S12).
 *
 * The board rings a short chime when a new order enters the board, so the
 * kitchen hears it without looking at the screen. The sound is made with the
 * Web Audio API of the browser — no dependency, no asset — and it can be
 * silenced from the board.
 *
 * The audio context is created on the first ring: browsers only allow an
 * audio context after an interaction of the user, and a context created
 * earlier would stay suspended.
 */

/** Constructor of an audio context, as the browser declares it. */
type AudioContextConstructor = new () => AudioContext;

/** Options of the chime. */
export interface KitchenChimeOptions {
  /**
   * Factory of the audio context.
   *
   * It exists so the tests can hand a recorder instead of a real context; by
   * default the context of the browser is used, and `null` when the browser
   * has none.
   */
  createContext?: () => AudioContext | null;
}

/** The chime of the kitchen. */
export interface KitchenChime {
  /** Whether the chime is silenced. */
  isMuted: () => boolean;
  /** Silences the chime, or lets it ring again. */
  setMuted: (muted: boolean) => void;
  /** Rings the chime of a new order; it stays silent when it is silenced. */
  play: () => void;
  /** Releases the audio context. */
  dispose: () => void;
}

/** Frequencies of the two notes of the chime, in hertz. */
const CHIME_NOTES = [880, 1174.66];

/** Duration of each note, in seconds. */
const CHIME_NOTE_DURATION = 0.16;

/** Delay between the two notes, in seconds. */
const CHIME_NOTE_GAP = 0.18;

/**
 * The audio context of the browser, or `null` when it has none.
 *
 * The lookup goes through a cast because the constructor is not declared in
 * every lib of the DOM and the board must not crash where it is missing.
 */
function browserAudioContext(): AudioContext | null {
  if (typeof window === 'undefined') {
    return null;
  }

  // SAFETY: the DOM lib declares the constructor as always present, but the
  // runtime may be jsdom or an old browser without it — the lookup has to
  // tolerate its absence instead of assuming the type.
  const candidate = (window as unknown as { AudioContext?: AudioContextConstructor }).AudioContext;

  if (typeof candidate !== 'function') {
    return null;
  }

  return new candidate();
}

/**
 * Ring the two notes of the chime.
 *
 * A failure of the audio device must never break the board, so the errors of
 * the context are swallowed.
 */
function ring(context: AudioContext): void {
  const start = context.currentTime;

  CHIME_NOTES.forEach((frequency, index) => {
    const at = start + index * CHIME_NOTE_GAP;
    const oscillator = context.createOscillator();
    const gain = context.createGain();

    oscillator.type = 'sine';
    oscillator.frequency.setValueAtTime(frequency, at);
    gain.gain.setValueAtTime(0.0001, at);
    gain.gain.exponentialRampToValueAtTime(0.2, at + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, at + CHIME_NOTE_DURATION);

    oscillator.connect(gain);
    gain.connect(context.destination);

    oscillator.start(at);
    oscillator.stop(at + CHIME_NOTE_DURATION + 0.02);
  });
}

/** Build the chime of the kitchen. */
export function createKitchenChime(options: KitchenChimeOptions = {}): KitchenChime {
  const createContext = options.createContext ?? browserAudioContext;

  let muted = false;
  let context: AudioContext | null = null;

  return {
    isMuted: () => muted,

    setMuted: (next: boolean) => {
      muted = next;
    },

    play: () => {
      if (muted) {
        return;
      }

      try {
        context ??= createContext();

        if (context === null) {
          return;
        }

        const resumed = context.resume?.();

        if (resumed instanceof Promise) {
          void resumed.catch(() => undefined);
        }

        ring(context);
      } catch {
        // A silent board is better than a broken one.
      }
    },

    dispose: () => {
      const open = context;

      context = null;

      if (open === null) {
        return;
      }

      const closed = open.close?.();

      if (closed instanceof Promise) {
        void closed.catch(() => undefined);
      }
    },
  };
}
