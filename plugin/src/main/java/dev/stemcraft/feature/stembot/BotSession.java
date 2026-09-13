package dev.stemcraft.feature.stembot;

import org.bukkit.Location;

import java.util.List;
import java.util.HashSet;
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
    /** Main-thread output boundary; implementations own speech presentation and actor departure. */
    public interface Output {
        /** Send an immediate message without delaying the action interpreter. */
        void say(String text);
        /** Speak a message and return the number of ticks before the next instruction may run. */
        int talk(String text);
        /**
         * Start a registered optional step. The provider may complete synchronously.
         * @param key namespaced provider key
         * @param completion receives verified success or provider failure on the server thread
         * @return non-null, idempotent cancellation callback
         */
        default Runnable await(String key, java.util.function.Consumer<Boolean> completion) {
            completion.accept(false);
            return () -> { };
        }
        /** Dispose of an actor, optionally after a presentation delay owned by the output. */
        default void depart(BotActor actor) { actor.close(); }
    }

    private enum WaitMode { NONE,SLEEP,TALK,WAVE,POINT,WALK,LISTEN,AWAIT,STUCK }

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
    private Location recoveryTarget;
    private BotActor.RouteSearch routeSearch;
    private List<Location> routeWaypoints;
    private int routeWaypointIndex;
    private int recoveryAttempts;
    private int nextScanningMessage;
    private int lastProgress;
    private boolean playerBehind;
    private volatile boolean closed;
    private volatile boolean chatEngaged=true;
    private int awayTicks;
    private long speechRevision;
    private long callbackRevision;
    private Runnable cancelCallback = () -> { };

    /** Create a session at a validated action; tick and input must run on the server thread. */
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

    /** @return the private actor controlled by this session */
    public BotActor actor() { return actor; }
    /** @return whether the session has terminated and no longer accepts input */
    public boolean closed() { return closed; }
    /** @return whether nearby ordinary chat should currently route to the guide */
    public boolean chatEngaged() { return chatEngaged&&!closed; }
    /** @return current script action identifier */
    public String action() { return action; }
    /** @return revision used to reject speech queued before an interruption */
    public long speechRevision() { return speechRevision; }

    /** Recognise dismissal phrases independently of the current action or wait mode. */
    public static boolean isDismissal(String text) {
        return text.trim().matches("(?i)(?:please\\s+)?(?:bye|goodbye|good bye|close|exit|stop|cancel|go away|leave me alone)(?:\\s+please)?[.!?]*");
    }

    /** Route a private player reply, allowing recognised topics to interrupt an active action. */
    public void input(String text) {
        if(!chatEngaged()) return;
        idle=0;

        String input=text.trim();
        if(isDismissal(input)) {
            close();
            return;
        }

        if(waitMode==WaitMode.AWAIT && input.matches("(?i)(skip|later|not now)[.!]?")) {
            jumpTo(listening.get(1).target());
            return;
        }

        if(waitMode==WaitMode.STUCK) {
            if(input.matches("(?i)(?:retry|try again)[.!]?")) {
                waitMode=WaitMode.WALK;
                resetRecovery();
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
            if(closed) return;
        }

        for(BotScript.ListenRoute route:replyRoutes()) {
            if(!route.matches(input)) continue;

            speechRevision++;
            actor.cancel();
            walkTarget=null;
            progress=null;
            playerBehind=false;
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
        if(waitMode==WaitMode.STUCK) stuckPrompt();
    }

    /** Find the upcoming reply prompt without executing its intervening actions. */
    private List<BotScript.ListenRoute> replyRoutes() {
        if(waitMode==WaitMode.LISTEN) return listening;
        String nextAction=action;
        int nextPc=pc;
        var visited=new HashSet<String>();
        while(visited.add(nextAction+":"+nextPc)) {
            var program=script.actions().get(nextAction);
            if(program==null||nextPc>=program.size()) return List.of();
            var instruction=program.get(nextPc++);
            switch(instruction.op()) {
                case LISTEN -> { return instruction.routes(); }
                case ACTION -> { nextAction=instruction.text(); nextPc=0; }
                case END,CLOSE -> { return List.of(); }
                default -> { }
            }
        }
        return List.of();
    }

    /** Advance one five-tick scheduler interval using the owner location for proximity and navigation checks. */
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
            case AWAIT -> {
                if(age < waitUntil) return;
                jumpTo(listening.get(1).target());
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
                    resetRecovery();
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

                case AWAIT -> await(instruction);

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

        if(Math.abs(here.getY() - walkTarget.getY()) <= .6
            && here.distanceSquared(walkTarget)<=script.arrivalDistance()*script.arrivalDistance()) {
            actor.cancel();
            resetRecovery();
            walkTarget=null;
            progress=null;
            return true;
        }

        if(recoveryTarget != null && Math.abs(here.getY() - recoveryTarget.getY()) <= .6
            && here.distanceSquared(recoveryTarget) <= .64) {
            recoveryTarget = null;
            resetWalkProgress();
        }

        boolean routeFailed = false;
        if(routeSearch != null) {
            routeWaypoints = routeSearch.advance();
            if(routeWaypoints == null) return false;
            routeSearch = null;
            routeWaypointIndex = 0;
            if(routeWaypoints.isEmpty()) {
                routeWaypoints = null;
                recoveryAttempts = 3;
                routeFailed = true;
            }
        }

        if(recoveryTarget == null && routeWaypoints != null) {
            if(routeWaypointIndex < routeWaypoints.size()) {
                Location candidate = routeWaypoints.get(routeWaypointIndex++);
                // Never skip an unreachable segment: it may be the only staircase out.
                if(actor.canNavigateTo(candidate)) {
                    recoveryTarget = candidate;
                    resetWalkProgress();
                    actor.move(candidate, walkSpeed);
                    return false;
                }
                recoveryAttempts = 3;
                routeFailed = true;
            }
            routeWaypoints = null;
        }

        if(progress==null||here.distanceSquared(progress)>.5) {
            progress=here.clone();
            lastProgress=age;
        }

        if(routeFailed || age-lastProgress>=200) {
            actor.cancel();
            if(recoveryAttempts < 3) {
                recoveryTarget = null;
                routeWaypoints = null;
                routeSearch = actor.findRoute(walkTarget);
                recoveryAttempts++;
                if(age >= nextScanningMessage) {
                    for(String line:script.randomSystemMessage(script.scanning())) output.say(line);
                    nextScanningMessage = age + script.scanningCooldownSeconds() * 20;
                }
                return false;
            }
            waitMode=WaitMode.STUCK;

            for(String line:script.randomSystemMessage(script.stuck()))
                output.say(line.replace("{action}",action)
                    .replace("{world}",world)
                    .replace("{target}",walkTarget.getBlockX()+" "+walkTarget.getBlockY()+" "+walkTarget.getBlockZ()));

            stuckPrompt();

            return false;
        }

        if(!actor.navigating()) {
            actor.move(recoveryTarget == null ? walkTarget : recoveryTarget,walkSpeed);
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

    private void resetRecovery() {
        recoveryTarget = null;
        routeWaypoints = null;
        routeWaypointIndex = 0;
        recoveryAttempts = 0;
        routeSearch = null;
    }

    private void cancelCallback() {
        callbackRevision++;
        Runnable cancel = cancelCallback;
        cancelCallback = () -> { };
        cancel.run();
    }

    private void await(BotScript.Instruction instruction) {
        cancelCallback();
        long revision = callbackRevision;
        listening = instruction.routes();
        waitMode = WaitMode.AWAIT;
        waitUntil = age + instruction.ticks();
        Runnable cancellation = output.await(instruction.text(), success -> {
            if (closed || revision != callbackRevision || waitMode != WaitMode.AWAIT) return;
            idle = 0;
            jumpTo(instruction.routes().get(success ? 0 : 1).target());
        });
        if (revision == callbackRevision && waitMode == WaitMode.AWAIT) cancelCallback = cancellation;
        else cancellation.run(); // Includes providers that complete synchronously.
    }

    private void jumpTo(String target) {
        if(!script.actions().containsKey(target))
            throw new IllegalStateException("Unknown STEMBot action: "+target);

        cancelCallback();
        action=target;
        pc=0;
        waitMode=WaitMode.NONE;
        listening=List.of();
    }

    /** End the session with its configured farewell. Idempotent. */
    public void close() {
        close(true);
    }

    /** End the session, optionally suppressing farewell when replacing the guide elsewhere. */
    public void close(boolean farewell) {
        if(closed) return;
        closed=true;
        resetRecovery();
        speechRevision++;
        cancelCallback();

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
