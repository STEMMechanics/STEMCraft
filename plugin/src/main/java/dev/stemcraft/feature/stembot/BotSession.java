package dev.stemcraft.feature.stembot;

import org.bukkit.Location;
import java.util.List;
import java.util.function.Consumer;

/** Main-thread conversation/tour state machine. Navigation itself remains with Citizens. */
public final class BotSession {
    private final BotScript script;
    private final BotActor actor;
    private final String world;
    private final Consumer<List<String>> output;
    private String state="ready";
    private List<String> last=List.of();
    private int age,idle,index,pauseUntil,nextPath,lastProgress,nextJump;
    private Location progress;
    private boolean touring,paused,waiting,arrived,closed;
    public BotSession(BotScript script,BotActor actor,String world,Consumer<List<String>> output) {
        this.script=script;this.actor=actor;this.world=world;this.output=output;
        say(script.greeting());
    }
    public BotActor actor() { return actor; }
    public boolean closed() { return closed; }
    public void input(String text) {
        idle=0;
        if(text.length()>256) { say(script.fallback());return; }
        var reply=script.respond(world,state,text.trim());
        if(!reply.nextState().isBlank()) state=reply.nextState();
        say(reply.say());
        for(String action:reply.actions()) {
            switch(action) {
                case "@close" -> { close();return; }
                case "@tour" -> startTour();
                case "@pause" -> { paused=true;actor.pause(true); }
                case "@resume" -> { paused=false;actor.pause(false);resetProgress(); }
                case "@repeat" -> output.accept(last);
                default -> throw new IllegalStateException("Unvalidated STEMBot action");
            }
        }
    }
    public void startTour() {
        if(script.tours().getOrDefault(world,List.of()).isEmpty()) { say(script.fallback());return; }
        actor.cancel();touring=true;paused=false;waiting=false;arrived=false;index=0;state="tour";resetProgress();
    }
    public void tick(Location owner) {
        age+=5;idle+=5;
        if(closed) return;
        if(!actor.valid()||!owner.getWorld().getName().equals(world)) { close();return; }
        if(idle>=script.idleSeconds()*20) { close();return; }
        if(age<=script.crouches()*10) { actor.sneak(age%10==5); return; }
        if(!touring||paused) return;
        var here=actor.location();
        double distance=here.distanceSquared(owner);
        if(distance>script.waitDistance()*script.waitDistance() || waiting && distance>script.resumeDistance()*script.resumeDistance()) {
            if(!waiting) { waiting=true;actor.pause(true);say(script.waiting()); }
            return;
        }
        if(waiting) { waiting=false;actor.pause(false);resetProgress(); }
        var route=script.tours().getOrDefault(world,List.of());
        if(index>=route.size()) { touring=false;state="questions";actor.cancel();say(script.complete());return; }
        var step=route.get(index);
        var p=step.point();
        var target=new Location(owner.getWorld(),p.x(),p.y(),p.z(),p.yaw(),0);
        if(here.distanceSquared(target)<=1.5) {
            actor.cancel();actor.face(p.yaw());
            if(owner.distanceSquared(here)>script.resumeDistance()*script.resumeDistance()) return;
            if(!arrived) { arrived=true;pauseUntil=age+step.pauseTicks();say(step.say()); }
            if(age>=pauseUntil) { index++;arrived=false;resetProgress(); }
            return;
        }
        if(arrived) { arrived=false; } // A knockback or edited path cannot skip its stop.
        if(progress==null||here.distanceSquared(progress)>.5) { progress=here.clone();lastProgress=age; }
        if(age-lastProgress>=200) {
            paused=true;actor.cancel();say(script.stuck());return;
        }
        if(!actor.navigating()&&age>=nextPath) { actor.move(target,script.speed());nextPath=age+40; }
        if(actor.navigating()&&age>=nextJump) { actor.jump();nextJump=age+script.jumpTicks(); }
    }
    private void resetProgress() { progress=null;lastProgress=age;nextPath=age; }
    private void say(List<String> lines) { if(!lines.isEmpty()) { last=lines;output.accept(lines); } }
    public void close() {
        if(closed) return;
        closed=true;
        try { actor.close(); } finally { output.accept(script.farewell()); }
    }
}
