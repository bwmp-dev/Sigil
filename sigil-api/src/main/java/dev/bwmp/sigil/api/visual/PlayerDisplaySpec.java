package dev.bwmp.sigil.api.visual;

import org.bukkit.Material;

/** Configuration for a display-entity shell that follows a player. */
public record PlayerDisplaySpec(Material material, double radius, double height) {

    public PlayerDisplaySpec {
        if (material == null || !material.isBlock()) {
            throw new IllegalArgumentException("material must be a block");
        }
        if (!Double.isFinite(radius) || radius < 0.5 || radius > 8.0) {
            throw new IllegalArgumentException("radius must be between 0.5 and 8 blocks");
        }
        if (!Double.isFinite(height) || height < 0.5 || height > 8.0) {
            throw new IllegalArgumentException("height must be between 0.5 and 8 blocks");
        }
    }
}
