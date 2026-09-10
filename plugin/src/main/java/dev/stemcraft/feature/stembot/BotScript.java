package dev.stemcraft.feature.stembot;

import dev.stemcraft.api.config.ConfigSection;
import java.util.*;
import java.util.regex.Pattern;

/** Immutable, validated dialogue and route configuration. Rules are evaluated in file order. */
public record BotScript(String name, String skinUrl, boolean slim, String firstWorld, Point firstSpawn,
                        double spawnDistance, double waitDistance, double resumeDistance, double speed,
                        int jumpTicks, int crouches, int idleSeconds, List<String> greeting, List<String> fallback,
                        List<String> farewell, List<String> waiting, List<String> stuck, List<String> complete,
                        Map<String,List<Waypoint>> tours, Map<String,List<Rule>> worldRules, List<Rule> rules) {
    public record Point(double x,double y,double z,float yaw) {}
    public record Waypoint(String id,Point point,int pauseTicks,List<String> say) {}
    public record Reply(List<String> say,List<String> actions,String nextState) {}
    public record Rule(Pattern pattern,String state,Reply reply) {
        boolean matches(String text,String current) { return (state.equals("*")||state.equals(current)) && pattern.matcher(text).matches(); }
    }
    private static final Set<String> ACTIONS=Set.of("@close","@tour","@pause","@resume","@repeat");
    public Reply respond(String world,String state,String text) {
        for (Rule r:worldRules.getOrDefault(world,List.of())) if(r.matches(text,state)) return r.reply();
        for (Rule r:rules) if(r.matches(text,state)) return r.reply();
        return new Reply(fallback,List.of(),state);
    }
    public static BotScript read(ConfigSection config) {
        Map<String,List<Waypoint>> tours=new LinkedHashMap<>();
        Map<String,List<Rule>> overrides=new LinkedHashMap<>();
        ConfigSection worlds=config.getSection("worlds",false);
        if(worlds!=null) for(String world:worlds.getKeys(false)) {
            var section=worlds.getSection(world);
            var route=section.getSection("waypoints",false);
            List<Waypoint> points=new ArrayList<>();
            if(route!=null) for(String id:route.getKeys(false)) {
                var step=route.getSection(id);
                points.add(new Waypoint(id,point(step),bounded(step.getInt("pause-ticks",100),0,2400),List.copyOf(step.getStringList("say"))));
            }
            tours.put(world,List.copyOf(points));
            overrides.put(world,readRules(section.getSection("responses",false)));
        }
        double wait=number(config.getDouble("movement.wait-distance",10),2,64);
        double resume=number(config.getDouble("movement.resume-distance",6),1,wait-1);
        return new BotScript(config.getString("name","STEMBot"),config.getString("skin.url",""),config.getBoolean("skin.slim",false),
            config.getString("first-join.world","world"),point(config.getSection("first-join.spawn")),
            number(config.getDouble("spawn-distance",2.5),1,6),wait,resume,number(config.getDouble("movement.speed",1.15),.2,2),
            bounded(config.getInt("movement.jump-interval-ticks",35),10,200),bounded(config.getInt("greeting-crouches",3),0,10),
            bounded(config.getInt("idle-timeout-seconds",600),30,3600),
            lines(config,"greeting"),lines(config,"fallback"),lines(config,"farewell"),lines(config,"waiting"),
            lines(config,"stuck"),lines(config,"complete"),Map.copyOf(tours),Map.copyOf(overrides),readRules(config.getSection("responses",false)));
    }
    private static List<String> lines(ConfigSection c,String key) { return List.copyOf(c.getStringList("messages."+key)); }
    private static List<Rule> readRules(ConfigSection section) {
        if(section==null) return List.of();
        List<Rule> result=new ArrayList<>();
        for(String id:section.getKeys(false)) {
            var r=section.getSection(id);
            var actions=List.copyOf(r.getStringList("actions"));
            for(String a:actions) if(!ACTIONS.contains(a)) throw new IllegalArgumentException("Unknown STEMBot action: "+a);
            result.add(new Rule(Pattern.compile(r.getString("regex"),Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE),
                r.getString("state","*"),new Reply(List.copyOf(r.getStringList("say")),actions,r.getString("next-state",""))));
        }
        return List.copyOf(result);
    }
    private static Point point(ConfigSection c) {
        String facing=c.getString("facing","S").toUpperCase(Locale.ROOT);
        float yaw=switch(facing) { case "N"->180;case "E"->-90;case "S"->0;case "W"->90;default->throw new IllegalArgumentException("Invalid facing: "+facing); };
        return new Point(number(c.getDouble("x"),-30_000_000,30_000_000),number(c.getDouble("y"),-2048,2048),
            number(c.getDouble("z"),-30_000_000,30_000_000),yaw);
    }
    private static double number(double n,double low,double high) {
        if(!Double.isFinite(n)||n<low||n>high) throw new IllegalArgumentException("STEMBot setting outside "+low+".."+high);
        return n;
    }
    private static int bounded(int n,int low,int high) { return (int)number(n,low,high); }
}
