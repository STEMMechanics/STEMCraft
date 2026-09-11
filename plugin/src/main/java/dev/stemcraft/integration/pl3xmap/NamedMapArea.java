package dev.stemcraft.integration.pl3xmap;

import java.util.List;

/** A persistent named shape displayed on Pl3xMap. */
public record NamedMapArea(String id, String world, String name, String type,
                           List<List<MapPoint>> boundaries) {
    public record MapPoint(int x, int z) { }
}
