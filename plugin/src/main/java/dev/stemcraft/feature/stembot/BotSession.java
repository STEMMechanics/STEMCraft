package dev.stemcraft.feature.stembot;

import org.bukkit.Location;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Main-thread STEMBot action interpreter.
 *
 * Blocking instructions:
 * - talk: waits for the returned speech duration
 * - sleep: waits the configured ticks
 * - walk: waits until Citizens reaches the target
 * - wave / point: briefly wait for the gesture
 * - listen: waits for matching private chat input
 */
public final class BotSession {
    public interface Output {
        void say(String text);
        int talk(String text);
        default void depart(BotActor actor) { actor.close(); }
    }

    private enum WaitMode { NONE,SLEEP,TALK,WAVE,POINT,WALK,LISTEN,STUCK }

    private final BotScript script;
    private final BotActor actor;
    private final String world;
    private final Output output;

    private String action;
    private int pc;
    private int age;
    private int idle;
    private int waitUntil;
    private int nextGesture;
    private double speed;
    private double walkSpeed;
    private WaitMode waitMode=WaitMode.NONE;
    private List<BotScript.ListenRoute> listening=List.of();
    private Location walkTarget;
    private Location progress;
    private int lastProgress;
    private boolean playerBehind;
    private volatile boolean closed;
    private volatile boolean chatEngaged=true;
    private int awayTicks;

    public BotSession(
        BotScript script,
        BotActor actor,
        String world,
        String initialAction,
        Output output
    ) {
        this.script=script;
        this.actor=actor;
        this.world=world;
        this.output=output;
        this.speed=script.defaultSpeed();
        jumpTo(initialAction);
    }

    public BotActor actor() { return actor; }
    public boolean closed() { return closed; }
    public boolean chatEngaged() { return chatEngaged&&!closed; }
    public String action() { return action; }

    public void input(String text) {
        if(!chatEngaged()) return;
        idle=0;

        String input=text.trim();

        if(waitMode==WaitMode.STUCK) {
            if(input.matches("(?i)(?:retry|try again)[.!]?")) {
                waitMode=WaitMode.WALK;
                resetWalkProgress();
                return;
            }
            if(input.matches("(?i)(?:yes|yep|continue|resume|ready|skip)[.!]?")) {
                walkTarget=null;
                progress=null;
                waitMode=WaitMode.NONE;
                return;
            }
            if(input.matches("(?i)(?:no|nope|bye|close|exit)[.!]?")) {
                close();
            }
            if(!closed) stuckPrompt();
            return;
        }

        if(waitMode!=WaitMode.LISTEN) return;

        for(BotScript.ListenRoute route:listening) {
            if(!route.matches(input)) continue;

            listening=List.of();
            waitMode=WaitMode.NONE;

            if(route.target().equalsIgnoreCase("close")
                ||route.target().equalsIgnoreCase("end")) {
                close();
            } else {
                jumpTo(route.target());
            }
            return;
        }
    }

    public void tick(Location owner) {
        idle+=5;

        if(closed) return;

        if(!actor.valid()
            ||owner.getWorld()==null
            ||!owner.getWorld().getName().equals(world)) {
            close(chatEngaged);
            return;
        }

        if(idle>=script.idleSeconds()*20) {
            close(chatEngaged);
            return;
        }

        double distance=actor.location().distanceSquared(owner);
        if(chatEngaged&&distance>script.chat().disengageDistance()*script.chat().disengageDistance()) {
            chatEngaged=false;
            awayTicks=0;
            actor.cancel();
            output.say(script.chat().disengaged()
                .replace("{seconds}",Integer.toString(script.chat().awayTimeoutSeconds())));
        } else if(!chatEngaged&&distance<=script.chat().reengageDistance()*script.chat().reengageDistance()) {
            chatEngaged=true;
            awayTicks=0;
            idle=0;
            if(waitMode==WaitMode.WALK) resetWalkProgress();
            output.say(script.chat().reengaged());
        }

        if(!chatEngaged) {
            awayTicks+=5;
            if(awayTicks>=script.chat().awayTimeoutSeconds()*20) close(false);
            return;
        }

        // Freeze script timers while away, but keep the cleanup timer running.
        age+=5;

        switch(waitMode) {
            case SLEEP -> {
                if(age>=waitUntil) waitMode=WaitMode.NONE;
                else return;
            }
            case TALK -> {
                if(age>=waitUntil) {
                    waitMode=WaitMode.SLEEP;
                    waitUntil=age+script.speech().postDelayTicks();
                    if(script.speech().postDelayTicks()>0) return;
                    waitMode=WaitMode.NONE;
                    break;
                }
                animateTalk(owner);
                return;
            }
            case WAVE -> {
                animateWave(owner);
                if(age>=waitUntil) waitMode=WaitMode.NONE;
                else return;
            }
            case POINT -> {
                if(age>=waitUntil) waitMode=WaitMode.NONE;
                else return;
            }
            case WALK -> {
                if(!tickWalk(owner)) return;
                waitMode=WaitMode.NONE;
            }
            case LISTEN,STUCK -> {
                return;
            }
            case NONE -> {}
        }

        run(owner);
    }

    private void run(Location owner) {
        int guard=0;

        while(!closed&&waitMode==WaitMode.NONE&&guard++<64) {
            List<BotScript.Instruction> program=script.actions().get(action);
            if(program==null)
                throw new IllegalStateException("Unknown STEMBot action: "+action);

            if(pc>=program.size()) {
                close();
                return;
            }

            BotScript.Instruction instruction=program.get(pc++);

            switch(instruction.op()) {
                case SAY -> output.say(instruction.text());

                case TALK -> {
                    int duration=Math.max(1,output.talk(instruction.text()));
                    waitMode=WaitMode.TALK;
                    waitUntil=age+duration;
                    nextGesture=age;
                }

                case SLEEP -> {
                    waitMode=WaitMode.SLEEP;
                    waitUntil=age+instruction.ticks();
                }

                case SPEED -> speed=instruction.number();

                case WALK -> {
                    walkTarget=new Location(
                        owner.getWorld(),
                        instruction.x(),
                        instruction.y(),
                        instruction.z()
                    );

                    walkSpeed=instruction.number()>0
                        ?instruction.number()
                        :speed;

                    waitMode=WaitMode.WALK;
                    resetWalkProgress();

                    if(!tickWalk(owner)) return;
                    waitMode=WaitMode.NONE;
                }

                case LOOK -> {
                    if("player".equals(instruction.text()))
                        actor.lookAt(owner.clone().add(0,1.4,0));
                    else
                        actor.lookAt(new Location(
                            owner.getWorld(),
                            instruction.x(),
                            instruction.y(),
                            instruction.z()
                        ));
                }

                case POINT -> {
                    Location target="player".equals(instruction.text())
                        ?owner.clone().add(0,1.2,0)
                        :new Location(
                            owner.getWorld(),
                            instruction.x(),
                            instruction.y(),
                            instruction.z()
                        );

                    actor.lookAt(target);
                    actor.animate("ARM_SWING");
                    waitMode=WaitMode.POINT;
                    waitUntil=age+12;
                }

                case WAVE -> {
                    actor.lookAt(owner.clone().add(0,1.4,0));
                    actor.animate("ARM_SWING");
                    waitMode=WaitMode.WAVE;
                    waitUntil=age+22;
                    nextGesture=age+7;
                }

                case SNEAK -> actor.sneak(true);
                case STAND -> actor.sneak(false);

                case ACTION -> jumpTo(instruction.text());

                case LISTEN -> {
                    listening=instruction.routes();
                    waitMode=WaitMode.LISTEN;
                }

                case END,CLOSE -> close();
            }
        }

        if(guard>=64)
            throw new IllegalStateException(
                "STEMBot action loop exceeded 64 immediate instructions; check action:"+action);
    }

    /**
     * @return true when the walk has completed.
     */
    private boolean tickWalk(Location owner) {
        if(walkTarget==null)
            throw new IllegalStateException("STEMBot WALK state has no target");

        Location here=actor.location();

        double playerDistance=here.distanceSquared(owner);
        double waitDistance=script.waitDistance();
        double resumeDistance=script.resumeDistance();

        if(!playerBehind&&playerDistance>waitDistance*waitDistance) {
            playerBehind=true;
            actor.cancel();

            for(String line:script.randomSystemMessage(script.waiting()))
                output.say(line);

            return false;
        }

        if(playerBehind) {
            if(playerDistance>resumeDistance*resumeDistance)
                return false;

            playerBehind=false;
            resetWalkProgress();
        }

        if(here.distanceSquared(walkTarget)<=script.arrivalDistance()*script.arrivalDistance()) {
            actor.cancel();
            walkTarget=null;
            progress=null;
            return true;
        }

        if(progress==null||here.distanceSquared(progress)>.5) {
            progress=here.clone();
            lastProgress=age;
        }

        if(age-lastProgress>=200) {
            actor.cancel();
            waitMode=WaitMode.STUCK;

            for(String line:script.randomSystemMessage(script.stuck()))
                output.say(line.replace("{action}",action)
                    .replace("{world}",world)
                    .replace("{target}",walkTarget.getBlockX()+" "+walkTarget.getBlockY()+" "+walkTarget.getBlockZ()));

            stuckPrompt();

            return false;
        }

        if(!actor.navigating()) {
            actor.move(walkTarget,walkSpeed);
        }

        return false;
    }

    private void animateTalk(Location owner) {
        if(age<nextGesture) return;

        actor.lookAt(owner.clone().add(0,1.4,0));

        // Mostly main-hand movement, occasionally off-hand, to avoid a rigid repeated wave.
        actor.animate(ThreadLocalRandom.current().nextInt(4)==0
            ?"ARM_SWING_OFFHAND"
            :"ARM_SWING");

        nextGesture=age+ThreadLocalRandom.current().nextInt(
            script.speech().talkGestureMinTicks(),
            script.speech().talkGestureMaxTicks()+1
        );
    }

    private void stuckPrompt() {
        boolean more=pc<script.actions().get(action).size();
        output.say(more
            ?":chat_bubble: &eShall we carry on from here? Reply continue to skip this walk, retry to try it again, or bye to finish."
            :":chat_bubble: &eThis was my last step. Reply retry to try again, or bye to finish.");
    }

    private void animateWave(Location owner) {
        actor.lookAt(owner.clone().add(0,1.4,0));

        if(age>=nextGesture&&age<waitUntil) {
            actor.animate("ARM_SWING");
            nextGesture=age+7;
        }
    }

    private void resetWalkProgress() {
        actor.cancel();
        progress=null;
        lastProgress=age;
    }

    private void jumpTo(String target) {
        if(!script.actions().containsKey(target))
            throw new IllegalStateException("Unknown STEMBot action: "+target);

        action=target;
        pc=0;
        waitMode=WaitMode.NONE;
        listening=List.of();
    }

    public void close() {
        close(true);
    }

    public void close(boolean farewell) {
        if(closed) return;
        closed=true;

        try {
            actor.cancel();
            actor.sneak(false);
        } finally {
            try {
                output.depart(actor);
            } finally {
                if(farewell)
                    for(String line:script.farewell())
                        output.say(line);
            }
        }
    }
}
