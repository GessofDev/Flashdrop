import { vi } from 'vitest';

/**
 * Fake of the Web Audio API.
 *
 * jsdom does not implement the audio context, so the tests replace it with
 * this recorder: it counts the notes the chime starts, without a sound.
 */
export interface AudioMock {
  /** Number of oscillators the chime started. */
  starts: () => number;
}

/** Replace the global audio context with the recorder. */
export function stubAudioContext(): AudioMock {
  const state = { starts: 0 };

  class FakeAudioParam {
    value = 0;

    setValueAtTime(): void {
      // The real parameter records the value; the recorder does not need it.
    }

    exponentialRampToValueAtTime(): void {
      // The real parameter records the value; the recorder does not need it.
    }

    linearRampToValueAtTime(): void {
      // The real parameter records the value; the recorder does not need it.
    }
  }

  class FakeAudioNode {
    connect(): void {
      // The real node returns the destination; the recorder does not need it.
    }
  }

  class FakeOscillatorNode extends FakeAudioNode {
    type = 'sine';

    frequency = new FakeAudioParam();

    start(): void {
      state.starts += 1;
    }

    stop(): void {
      // The real oscillator stops the sound; the recorder does not need it.
    }
  }

  class FakeGainNode extends FakeAudioNode {
    gain = new FakeAudioParam();
  }

  class FakeAudioContext {
    currentTime = 0;

    destination = new FakeAudioNode();

    state = 'running';

    createOscillator(): FakeOscillatorNode {
      return new FakeOscillatorNode();
    }

    createGain(): FakeGainNode {
      return new FakeGainNode();
    }

    resume(): Promise<void> {
      return Promise.resolve();
    }

    close(): Promise<void> {
      return Promise.resolve();
    }
  }

  vi.stubGlobal('AudioContext', FakeAudioContext);

  return { starts: () => state.starts };
}
