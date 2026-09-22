package dev.amado.minepiano.audio;

/** SoundFont source and allocation-free voicing applied when a voice starts. */
public enum PianoPreset {
    REALISTIC("realistic", null, 0.82f, 1.00f, 1.00f, 1.00f, 0.0f, 0.00f, DefaultVelocity.EXPONENT),
    WARM("warm", null, 0.88f, 1.12f, 1.20f, 0.95f, -2.0f, 0.18f, 1.80f),
    BRIGHT("bright", null, 0.92f, 0.72f, 0.78f, 1.08f, 3.0f, 0.00f, 2.25f),
    MELANCHOLIC("melancholic", null, 0.78f, 1.35f, 1.75f, 0.72f, -4.0f, 0.28f, 2.10f),
    SOFT("soft", null, 0.68f, 1.55f, 1.28f, 0.82f, 0.0f, 0.22f, 1.35f),
    DARK("dark", null, 0.84f, 1.08f, 1.35f, 0.88f, -3.0f, 0.55f, 1.90f),
    MUSIC_BOX("music_box", null, 0.72f, 0.35f, 0.42f, 0.38f, 12.0f, 0.00f, 1.65f),
    DETUNED("detuned", null, 0.78f, 0.90f, 1.10f, 0.94f, 18.0f, 0.08f, 1.85f),
    CONCERT("concert", null, 0.88f, 0.92f, 2.40f, 1.06f, 1.5f, 0.05f, 2.05f),
    VINTAGE("vintage", null, 0.80f, 1.20f, 1.55f, 0.78f, -7.0f, 0.38f, 1.55f);

    public static final float DEFAULT_VELOCITY_EXPONENT = DefaultVelocity.EXPONENT;

    private final String id;
    private final String soundfontResource;
    private final float gain;
    private final float attackScale;
    private final float releaseScale;
    private final float sustainLevel;
    private final float detuneCents;
    private final float lowPass;
    private final float velocityExponent;

    PianoPreset(String id, String soundfontResource, float gain, float attackScale, float releaseScale,
                float sustainLevel, float detuneCents, float lowPass, float velocityExponent) {
        this.id = id;
        this.soundfontResource = soundfontResource;
        this.gain = gain;
        this.attackScale = attackScale;
        this.releaseScale = releaseScale;
        this.sustainLevel = sustainLevel;
        this.detuneCents = detuneCents;
        this.lowPass = lowPass;
        this.velocityExponent = velocityExponent;
    }

    public String translationKey() { return "gui.minepiano.preset." + id; }
    public String soundfontResource() { return soundfontResource; }
    public float gain() { return gain; }
    public float attackScale() { return attackScale; }
    public float releaseScale() { return releaseScale; }
    public float sustainLevel() { return sustainLevel; }
    public float detuneCents() { return detuneCents; }
    public float lowPass() { return lowPass; }
    public float velocityExponent() { return velocityExponent; }

    public PianoPreset offset(int amount) {
        PianoPreset[] presets = values();
        return presets[Math.floorMod(ordinal() + amount, presets.length)];
    }

    public static PianoPreset fromName(String name) {
        if (name != null) {
            for (PianoPreset preset : values()) if (preset.name().equalsIgnoreCase(name)) return preset;
        }
        return REALISTIC;
    }

    private static final class DefaultVelocity {
        private static final float EXPONENT = 2.0f;
    }
}
