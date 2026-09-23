package dev.amado.minepiano.audio;

import dev.amado.minepiano.audio.sf2.Sf2SoundBank;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/** Real-time piano engine backed by one allocation-free mixer thread. */
public final class PianoEngineImpl implements PianoEngine {
    private static final int NOTE_ON = 1;
    private static final int NOTE_OFF = 2;
    private static final int SUSTAIN = 3;
    private static final int ALL_NOTES_OFF = 4;
    private static final int MASTER_GAIN = 5;

    private final CommandQueue commands = new CommandQueue(4096);
    private final AtomicLongArray logicalNotes = new AtomicLongArray(2);
    private final AtomicBoolean allNotesOffPending = new AtomicBoolean();
    private final AudioMixer mixer;
    private final Sf2SoundBank sourceBank;

    public PianoEngineImpl() {
        this(new Sf2SoundBank());
    }

    public PianoEngineImpl(SoundBank soundBank) {
        this(soundBank, new LocalOutput());
    }

    public PianoEngineImpl(SoundBank soundBank, LocalOutput localOutput) {
        mixer = new AudioMixer(Objects.requireNonNull(soundBank), Objects.requireNonNull(localOutput),
                commands, logicalNotes, allNotesOffPending);
        sourceBank = soundBank instanceof Sf2SoundBank sf2 ? sf2 : null;
    }

    @Override
    public void noteOn(int midi, int velocity) {
        checkMidi(midi);
        if (velocity < 0 || velocity > 127) throw new IllegalArgumentException("Velocity must be in 0..127");
        if (velocity == 0) {
            noteOff(midi);
            return;
        }
        commands.offer(pack(NOTE_ON, midi | (velocity << 7)));
    }

    @Override
    public void noteOff(int midi) {
        checkMidi(midi);
        commands.offer(pack(NOTE_OFF, midi));
    }

    @Override
    public void setSustain(boolean on) {
        commands.offer(pack(SUSTAIN, on ? 1 : 0));
    }

    @Override
    public boolean isSustainOn() {
        return mixer.isSustainOn();
    }

    @Override
    public void allNotesOff() {
        allNotesOffPending.set(true);
        clearLogicalNotes();
        commands.offer(pack(ALL_NOTES_OFF, 0));
    }

    @Override
    public void setMasterGain(float gain) {
        if (!Float.isFinite(gain) || gain < 0.0f) throw new IllegalArgumentException("Gain must be finite and non-negative");
        commands.offer(pack(MASTER_GAIN, Float.floatToRawIntBits(gain) & 0xffff_ffffL));
    }

    @Override
    public void setPreset(PianoPreset preset) {
        Objects.requireNonNull(preset, "preset");
        if (sourceBank == null) return;
        mixer.setSoundBank(sourceBank.withPreset(preset));
    }

    PianoPreset currentPreset() {
        SoundBank bank = mixer.soundBank();
        return bank instanceof Sf2SoundBank sf2 ? sf2.preset() : null;
    }

    @Override
    public boolean isNoteActive(int midi) {
        checkMidi(midi);
        return !allNotesOffPending.get()
                && (logicalNotes.get(midi >>> 6) & (1L << (midi & 63))) != 0L;
    }

    @Override
    public void addFrameConsumer(FrameConsumer consumer) {
        mixer.addFrameConsumer(Objects.requireNonNull(consumer));
    }

    @Override
    public void start() {
        mixer.start();
    }

    @Override
    public void stop() {
        mixer.stop();
    }

    private void clearLogicalNotes() {
        logicalNotes.set(0, 0L);
        logicalNotes.set(1, 0L);
    }

    private static void checkMidi(int midi) {
        if (midi < 0 || midi > 127) throw new IllegalArgumentException("MIDI note must be in 0..127");
    }

    static long pack(int type, long value) {
        return ((long) type << 56) | (value & 0x00ff_ffff_ffff_ffffL);
    }

    static final class CommandQueue {
        private final int capacity;
        private final int mask;
        private final long[] values;
        private final AtomicLongArray sequences;
        private final AtomicLong enqueuePosition = new AtomicLong();
        private long dequeuePosition;

        CommandQueue(int capacity) {
            if (capacity <= 0 || (capacity & (capacity - 1)) != 0) {
                throw new IllegalArgumentException("Capacity must be a positive power of two");
            }
            this.capacity = capacity;
            mask = capacity - 1;
            values = new long[capacity];
            sequences = new AtomicLongArray(capacity);
            for (int i = 0; i < capacity; i++) sequences.set(i, i);
        }

        void offer(long value) {
            if (value == 0L) throw new IllegalArgumentException("Zero is reserved for an empty queue");
            int spins = 0;
            for (;;) {
                long position = enqueuePosition.get();
                int index = (int) position & mask;
                long difference = sequences.get(index) - position;
                if (difference == 0L && enqueuePosition.compareAndSet(position, position + 1L)) {
                    values[index] = value;
                    sequences.set(index, position + 1L);
                    return;
                }
                if (++spins < 1024) Thread.onSpinWait();
                else {
                    spins = 0;
                    java.util.concurrent.locks.LockSupport.parkNanos(1_000L);
                }
            }
        }

        long poll() {
            long position = dequeuePosition;
            int index = (int) position & mask;
            if (sequences.get(index) != position + 1L) return 0L;
            long value = values[index];
            sequences.set(index, position + capacity);
            dequeuePosition = position + 1L;
            return value;
        }
    }
}
