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

package dev.stemcraft.feature.underhalls;

import dev.stemcraft.api.service.database.DatabaseService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.*;

/** Durable placement provenance, permanent entrance tombstones and fixed exit destinations. */
public final class UnderhallsStore {
    public record Pos(UUID world, int x, int y, int z) {
        public static Pos of(Block block) { return new Pos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()); }
        public Pos offset(int dx, int dy, int dz) { return new Pos(world, x + dx, y + dy, z + dz); }
        public Location location() { return new Location(Bukkit.getWorld(world), x + .5, y, z + .5); }
        public String encode() { return world + "," + x + "," + y + "," + z; }
        public static Pos decode(String value) {
            String[] parts = value.split(",");
            if (parts.length != 4) throw new IllegalArgumentException("Invalid Underhalls position");
            return new Pos(UUID.fromString(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        }
    }
    public record Entrance(Pos origin, UUID maze, int tileX, int tileZ, boolean retired, Pos room) {
        public Entrance(Pos origin, UUID maze, int tileX, int tileZ, boolean retired) { this(origin, maze, tileX, tileZ, retired, null); }
        public Entrance retire() { return new Entrance(origin, maze, tileX, tileZ, true, room); }
    }
    private final DatabaseService database;
    private final Set<Pos> placed = new HashSet<>();
    private final Map<Pos, Entrance> entrances = new LinkedHashMap<>();
    private final Map<Pos, Pos> exits = new HashMap<>();

    public UnderhallsStore(DatabaseService database) { this.database = database; }
    public void load() {
        if (!database.execute("CREATE TABLE IF NOT EXISTS underhalls_state (kind TEXT NOT NULL, position TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(kind, position))")) {
            throw new IllegalStateException("Could not initialise Underhalls storage");
        }
        placed.clear(); entrances.clear(); exits.clear();
        database.queryEach("SELECT kind, position, value FROM underhalls_state", null, rs -> {
            Pos pos = Pos.decode(rs.getString("position"));
            String value = rs.getString("value");
            switch (rs.getString("kind")) {
                case "placed" -> placed.add(pos);
                case "exit" -> exits.put(pos, Pos.decode(value));
                case "entrance" -> {
                    String[] parts = value.split(",");
                    entrances.put(pos, new Entrance(pos, UUID.fromString(parts[0]), Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]), Boolean.parseBoolean(parts[3]), parts.length == 7 ?
                            new Pos(UUID.fromString(parts[0]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]), Integer.parseInt(parts[6])) : null));
                }
                default -> throw new IllegalStateException("Unknown Underhalls state kind");
            }
        });
    }
    private void write(String kind, Pos pos, String value) {
        int updated = database.update("INSERT OR REPLACE INTO underhalls_state(kind, position, value) VALUES (?, ?, ?)", ps -> {
            ps.setString(1, kind); ps.setString(2, pos.encode()); ps.setString(3, value);
        });
        if (updated != 1) throw new IllegalStateException("Could not persist Underhalls state");
    }
    public boolean isPlaced(Pos pos) { return placed.contains(pos); }
    public void placed(Pos pos) { write("placed", pos, ""); placed.add(pos); }
    public void removed(Pos pos) {
        if (!placed.contains(pos)) return;
        int updated = database.update("DELETE FROM underhalls_state WHERE kind = 'placed' AND position = ?", ps -> ps.setString(1, pos.encode()));
        if (updated != 1) throw new IllegalStateException("Could not remove Underhalls placement record");
        placed.remove(pos);
    }
    public Collection<Entrance> entrances() { return List.copyOf(entrances.values()); }
    public Entrance entrance(Pos pos) { return entrances.get(pos); }
    public void save(Entrance entrance) {
        Entrance previous = entrances.get(entrance.origin);
        if (previous != null && previous.retired && !entrance.retired) throw new IllegalStateException("A retired doorway cannot reopen");
        write("entrance", entrance.origin, entrance.maze + "," + entrance.tileX + "," + entrance.tileZ + "," + entrance.retired + (entrance.room == null ? "" : "," + entrance.room.x + "," + entrance.room.y + "," + entrance.room.z));
        entrances.put(entrance.origin, entrance);
    }
    public Map<Pos, Pos> exits() { return Map.copyOf(exits); }
    public Pos exit(Pos room) { return exits.get(room); }
    public Pos pinExit(Pos room, Pos destination) {
        Pos existing = exits.get(room);
        if (existing != null) return existing;
        write("exit", room, destination.encode());
        exits.put(room, destination);
        return destination;
    }
}
