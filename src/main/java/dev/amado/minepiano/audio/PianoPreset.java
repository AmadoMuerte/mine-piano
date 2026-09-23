package dev.amado.minepiano.audio;

/** SoundFont source and allocation-free voicing applied when a voice starts. */
public enum PianoPreset {
    REALISTIC("realistic", null, 0, 0, 0.82f, 1.00f, 1.00f, 1.00f, 0.0f, 0.00f),
    GRAND("grand", gm(), 0, 0, 0.82f, 1.00f, 1.00f, 1.00f, 0.0f, 0.00f),
    BRIGHT("bright", gm(), 0, 1, 0.80f, 0.90f, 0.90f, 1.00f, 0.0f, 0.00f),
    ELECTRIC("electric", gm(), 0, 4, 0.88f, 0.85f, 1.15f, 0.95f, 0.0f, 0.05f),
    HARPSICHORD("harpsichord", gm(), 0, 6, 0.76f, 0.55f, 0.55f, 0.70f, 0.0f, 0.00f),
    MUSIC_BOX("music_box", gm(), 0, 10, 0.72f, 0.65f, 0.90f, 0.75f, 0.0f, 0.00f),
    VIBRAPHONE("vibraphone", gm(), 0, 11, 0.78f, 0.90f, 1.30f, 0.92f, 0.0f, 0.04f),
    ORGAN("organ", gm(), 0, 19, 0.70f, 0.70f, 1.10f, 1.00f, 0.0f, 0.02f),
    MELANCHOLIC("melancholic", null, 0, 0, 0.78f, 1.35f, 1.75f, 0.72f, -4.0f, 0.28f),
    CONCERT("concert", gm(), 0, 0, 0.86f, 0.92f, 2.40f, 1.06f, 1.5f, 0.03f);

    public static final float DEFAULT_VELOCITY_EXPONENT = 2.0f;

    private final String id;
    private final String soundfontResource;
    private final int bank;
    private final int program;
    private final float gain;
    private final float attackScale;
    private final float releaseScale;
    private final float sustainLevel;
    private final float detuneCents;
    private final float lowPass;

    PianoPreset(String id, String soundfontResource, int bank, int program, float gain, float attackScale,
                float releaseScale, float sustainLevel, float detuneCents, float lowPass) {
        this.id = id;
        this.soundfontResource = soundfontResource;
        this.bank = bank;
        this.program = program;
        this.gain = gain;
        this.attackScale = attackScale;
        this.releaseScale = releaseScale;
        this.sustainLevel = sustainLevel;
        this.detuneCents = detuneCents;
        this.lowPass = lowPass;
    }

    public String translationKey() { return "gui.minepiano.preset." + id; }
    public String soundfontResource() { return soundfontResource; }
    public int bank() { return bank; }
    public int program() { return program; }
    public float gain() { return gain; }
    public float attackScale() { return attackScale; }
    public float releaseScale() { return releaseScale; }
    public float sustainLevel() { return sustainLevel; }
    public float detuneCents() { return detuneCents; }
    public float lowPass() { return lowPass; }

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

    private static String gm() { return "assets/minepiano/soundfont/generaluser.sf2"; }

}
