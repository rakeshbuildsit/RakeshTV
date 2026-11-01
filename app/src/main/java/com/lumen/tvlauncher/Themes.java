package com.lumen.tvlauncher;

/** Wallpaper gradients and highlight colours. Add your own here. */
final class Themes {
    static final String[] NAMES = {
            "Midnight", "Aurora", "Sunset", "Ocean", "Forest", "Rose", "Pure Black"
    };

    static final int[][] GRADIENTS = {
            {0xFF0B1026, 0xFF1B1F4B, 0xFF2D1B4E},
            {0xFF051937, 0xFF004D7A, 0xFF00796B},
            {0xFF2B1055, 0xFF7B2869, 0xFFB5462C},
            {0xFF03045E, 0xFF023E8A, 0xFF0077B6},
            {0xFF081C15, 0xFF1B4332, 0xFF2D6A4F},
            {0xFF2E1A1F, 0xFF5E2F3C, 0xFF8E4A57},
            {0xFF000000, 0xFF050505, 0xFF0D0D0D},
    };

    static final String[] ACCENT_NAMES = {"Sky blue", "Mint", "Amber", "Coral", "Violet", "White"};

    static final int[] ACCENTS = {
            0xFF4FC3F7, 0xFF64FFDA, 0xFFFFC107, 0xFFFF7A6B, 0xFFB388FF, 0xFFFFFFFF
    };

    private Themes() {}
}
