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

import org.bukkit.*;
import java.util.ArrayList;
import java.util.function.Predicate;
import static dev.stemcraft.feature.portal.PortalPattern.Offset;

/** Floating exits with a clear, short fall onto verified terrain. */
final class AirDropExit {
    private AirDropExit() {}
    static PortalExitBuilder.Plan find(World world, SurvivalPortalType type, int x, int z,
                                      int rotation, int preferredY, Predicate<Location> blocked) {
        var b = PortalExitBuilder.bounds(type.pattern(), rotation);
        int low = world.getMinHeight()+7-b.minY(), high = world.getMaxHeight()-1-b.maxY();
        if (low > high) return null;
        int preferred = Math.clamp(preferredY,low,high);
        for (int radius=0; radius<=type.searchRadius(); radius+=8)
            for (int dx=-radius; dx<=radius; dx+=8) for (int dz=-radius; dz<=radius; dz+=8) {
                if (Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
                for (int step=0;step<=2*(high-low);step++) {
                    int y=preferred+(step==0?0:(step+1)/2*(step%2==1?1:-1));
                    if (y<low || y>high) continue;
                    var origin=new Offset(x+dx,y,z+dz);
                    // Cheap central surface rejection before checking the whole footprint.
                    var center=center(type,origin,rotation);
                    var floor=new Offset(center.x(),y+b.minY()-6,center.z());
                    if (!PortalExitBuilder.accessible(world,floor,blocked) || !safe(material(world,floor))) continue;
                    var plan=at(world,type,origin,rotation,blocked);
                    if (plan!=null) return plan;
                }
            }
        return null;
    }
    static PortalExitBuilder.Plan at(World world, SurvivalPortalType type, Offset origin,
                                    int rotation, Predicate<Location> blocked) {
        if (landing(world,type,origin,rotation,at->!blocked.test(at))==null) return null;
        var blocks=new ArrayList<PortalExitBuilder.Placement>();
        for (var cell:type.pattern().cells()) {
            var p=origin.add(cell.offset().rotate(rotation));
            if (!PortalExitBuilder.accessible(world,p,blocked) || !material(world,p).isAir()) return null;
            blocks.add(new PortalExitBuilder.Placement(p,cell.material().createBlockData()));
        }
        for (var cell:type.pattern().interior()) {
            var p=origin.add(cell.rotate(rotation));
            if (!PortalExitBuilder.accessible(world,p,blocked) || !material(world,p).isAir()) return null;
            blocks.add(new PortalExitBuilder.Placement(p,Material.AIR.createBlockData()));
        }
        return new PortalExitBuilder.Plan(origin,blocks);
    }
    static Location landing(World world, SurvivalPortalType type, Offset origin,
                            int rotation, Predicate<Location> allowed) {
        var b=PortalExitBuilder.bounds(type.pattern(),rotation);
        int bottom=origin.y()+b.minY();
        var center=center(type,origin,rotation);
        for(int x=origin.x()+b.minX()+2;x<=origin.x()+b.maxX()-2;x++)
            for(int z=origin.z()+b.minZ()+2;z<=origin.z()+b.maxZ()-2;z++) {
                for(int y=bottom-5;y<bottom;y++) {
                    var p=new Offset(x,y,z);
                    if (!PortalExitBuilder.accessible(world,p,at->!allowed.test(at)) || !material(world,p).isAir()) return null;
                }
                var floor=new Offset(x,bottom-6,z);
                if (!PortalExitBuilder.accessible(world,floor,at->!allowed.test(at))) return null;
                Material surface=material(world,floor);
                if (surface==Material.LAVA || surface==Material.MAGMA_BLOCK) return null;
                if (Math.abs(x-center.x())<=1 && Math.abs(z-center.z())<=1 && !safe(surface)) return null;
            }
        return new Location(world,center.x()+.5,bottom-2,center.z()+.5);
    }
    private static Offset center(SurvivalPortalType type, Offset origin, int rotation) {
        var b=PortalExitBuilder.bounds(type.pattern(),rotation);
        return origin.add(new Offset((b.minX()+b.maxX())/2,0,(b.minZ()+b.maxZ())/2));
    }
    private static Material material(World world, Offset p) { return world.getBlockAt(p.x(),p.y(),p.z()).getType(); }
    private static boolean safe(Material m) {
        return m==Material.WATER || m.isSolid() && m.isOccluding()
                && m!=Material.MAGMA_BLOCK && m!=Material.CACTUS && m!=Material.CAMPFIRE && m!=Material.SOUL_CAMPFIRE;
    }
}
