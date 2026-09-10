/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.feature.portal;

import org.bukkit.Material;
import java.util.*;

/** Bounded, immutable multiblock geometry. Coordinates are independent of Bukkit world state. */
public record PortalPattern(List<Cell> cells, List<Offset> interior, Material interiorMaterial) {
    public record Offset(int x, int y, int z) {
        public Offset rotate(int rotation) {
            return switch (Math.floorMod(rotation, 4)) {
                case 1 -> new Offset(-z, y, x);
                case 2 -> new Offset(-x, y, -z);
                case 3 -> new Offset(z, y, -x);
                default -> this;
            };
        }
        public Offset subtract(Offset other) { return new Offset(x - other.x, y - other.y, z - other.z); }
        public Offset add(Offset other) { return new Offset(x + other.x, y + other.y, z + other.z); }
    }
    public record Cell(Offset offset, Material material) {}
    public PortalPattern {
        cells = List.copyOf(cells); interior = List.copyOf(interior);
        Objects.requireNonNull(interiorMaterial);
        if (cells.isEmpty() || interior.isEmpty() || cells.size() + interior.size() > 512)
            throw new IllegalArgumentException("Portal requires frame and interior, at most 512 cells");
        Set<Offset> occupied = new HashSet<>();
        for (Cell c : cells) if (!occupied.add(c.offset())) throw new IllegalArgumentException("Duplicate portal cell");
        for (Offset o : interior) if (!occupied.add(o)) throw new IllegalArgumentException("Overlapping portal interior");
        for (Offset o : occupied) if (Math.abs(o.x()) > 16 || Math.abs(o.y()) > 16 || Math.abs(o.z()) > 16)
            throw new IllegalArgumentException("Portal cells must be within 16 blocks of the origin");
        if (interiorMaterial != Material.AIR && interiorMaterial != Material.NETHER_PORTAL)
            throw new IllegalArgumentException("Interior must be AIR or NETHER_PORTAL");
    }
    public static Offset offset(String value) {
        String[] parts = value.split(",");
        if (parts.length != 3) throw new IllegalArgumentException("Expected x,y,z: " + value);
        return new Offset(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
    }
    public static PortalPattern parse(List<String> frame, List<String> inside, Material material) {
        List<Cell> cells = new ArrayList<>();
        for (String line : frame) {
            String[] parts = line.split(":", 2);
            if (parts.length != 2) throw new IllegalArgumentException("Expected x,y,z:MATERIAL");
            Material block = Material.valueOf(parts[1].trim());
            if (!block.isBlock() || block.isAir()) throw new IllegalArgumentException("Frame must use a block material");
            cells.add(new Cell(offset(parts[0]), block));
        }
        return new PortalPattern(cells, inside.stream().map(PortalPattern::offset).toList(), material);
    }
}
