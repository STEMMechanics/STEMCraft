package dev.stemcraft.feature.stembot;

import dev.stemcraft.api.config.ConfigSection;

import java.util.*;
import java.util.regex.Pattern;

/** Parsed and validated STEMBot action program. */
public record BotScript(
    String name,
    String prefix,
    String replyFormat,
    String skinUrl,
    String skinValue,
    String skinSignature,
    boolean slim,
    double spawnDistance,
    double spawnSearchRadius,
    int departureDelayTicks,
    double arrivalDistance,
    double defaultSpeed,
    double waitDistance,
    double resumeDistance,
    int idleSeconds,
    ChatSettings chat,
    SpeechSettings speech,
    List<String> waiting,
    List<String> scanning,
    int scanningCooldownSeconds,
    List<String> stuck,
    List<String> farewell,
    Map<String,List<Instruction>> actions,
    Map<String,String> commandWorld,
    Map<String,String> firstTimeWorld
) {
    private static final List<String> DEFAULT_SCANNING_MESSAGES = List.of(
        "Beep boop... scanning terrain for a pathway...",
        "Bzzzt! Let me scan for another way around.",
        "Recalculating! Give my navigation circuits a moment."
    );
    public record ChatSettings(double disengageDistance,double reengageDistance,
                               int awayTimeoutSeconds,String disengaged,String reengaged) {}

    public enum Op {
        SAY,TALK,SLEEP,WALK,SPEED,LOOK,POINT,WAVE,SNEAK,STAND,ACTION,LISTEN,AWAIT,END,CLOSE
    }

    public record SpeechSettings(
        boolean enabled,
        String sound,
        float volume,
        float pitchMin,
        float pitchMax,
        int beepIntervalMin,
        int beepIntervalMax,
        int charactersPerBeep,
        int minBeeps,
        int maxBeeps,
        int talkGestureMinTicks,
        int talkGestureMaxTicks,
        int postDelayTicks
    ) {}

    public record Instruction(
        Op op,
        String text,
        double x,
        double y,
        double z,
        double number,
        int ticks,
        List<ListenRoute> routes
    ) {
        static Instruction simple(Op op) {
            return new Instruction(op,"",0,0,0,0,0,List.of());
        }
    }

    public record ListenRoute(List<Pattern> patterns,String target) {
        boolean matches(String input) {
            return patterns.stream().anyMatch(p->p.matcher(input).matches());
        }
    }

    public String commandAction(String world) {
        return commandWorld.get(world.toLowerCase(Locale.ROOT));
    }

    public String firstTimeAction(String world) {
        return firstTimeWorld.get(world.toLowerCase(Locale.ROOT));
    }

    public List<String> randomSystemMessage(List<String> choices) {
        if(choices.isEmpty()) return List.of();
        return List.of(choices.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(choices.size())));
    }

    public static BotScript read(ConfigSection config) {
        String skinValue=config.getString("skin.texture.value","");
        String skinSignature=config.getString("skin.texture.signature","");
        if(skinValue.isBlank()!=skinSignature.isBlank())
            throw new IllegalArgumentException(
                "skin.texture.value and skin.texture.signature must both be set or both be blank");

        double wait=bounded(config.getDouble("movement.wait-distance",10),2,64);
        double resume=bounded(config.getDouble("movement.resume-distance",6),1,wait-0.01);
        double chatDistance=bounded(config.getDouble("chat.disengage-distance",12),2,64);
        ChatSettings chat=new ChatSettings(
            chatDistance,
            bounded(config.getDouble("chat.reengage-distance",6),1,chatDistance-0.01),
            boundedInt(config.getInt("chat.away-timeout-seconds",60),5,3600),
            config.getString("messages.chat-disengaged",":chat_bubble: &eYou are out of range. Your chat is public now. Come closer to resume; I will power down after {seconds} seconds away."),
            config.getString("messages.chat-reengaged",":chat_bubble: &ePrivate chat with STEMBot resumed. Your messages come only to me again.")
        );

        int intervalMin=boundedInt(config.getInt("speech.beep-interval-ticks.min",2),1,40);
        int intervalMax=boundedInt(config.getInt("speech.beep-interval-ticks.max",6),intervalMin,40);
        int gestureMin=boundedInt(config.getInt("speech.talk-gesture-ticks.min",8),1,100);
        int gestureMax=boundedInt(config.getInt("speech.talk-gesture-ticks.max",18),gestureMin,200);

        SpeechSettings speech=new SpeechSettings(
            config.getBoolean("speech.enabled",true),
            config.getString("speech.sound","BLOCK_NOTE_BLOCK_BIT").toLowerCase(Locale.ROOT),
            (float)bounded(config.getDouble("speech.volume",0.22),0,4),
            (float)bounded(config.getDouble("speech.pitch-min",1.25),0.5,2),
            (float)bounded(config.getDouble("speech.pitch-max",1.55),0.5,2),
            intervalMin,
            intervalMax,
            boundedInt(config.getInt("speech.characters-per-beep",6),1,64),
            boundedInt(config.getInt("speech.min-beeps",2),1,100),
            boundedInt(config.getInt("speech.max-beeps",30),1,200),
            gestureMin,
            gestureMax,
            boundedInt(config.getInt("speech.post-delay-ticks",60),0,1200)
        );

        if(speech.pitchMax()<speech.pitchMin())
            throw new IllegalArgumentException("speech.pitch-max must be >= speech.pitch-min");
        if(speech.maxBeeps()<speech.minBeeps())
            throw new IllegalArgumentException("speech.max-beeps must be >= speech.min-beeps");

        Map<String,List<Instruction>> actions=readActions(config.getSection("actions",false));
        Map<String,String> commandWorld=readRoutes(config.getSection("command.world",false));
        Map<String,String> firstTimeWorld=readRoutes(config.getSection("first-time.world",false));

        validateTargets(actions,commandWorld,firstTimeWorld);

        return new BotScript(
            config.getString("name","STEMBot"),
            config.getString("messages.prefix",":stembot: &bSTEMBot: &f"),
            config.getString("messages.reply-format",":chat_bubble: &7You → {bot}: {message}"),
            config.getString("skin.url",""),
            skinValue,
            skinSignature,
            config.getBoolean("skin.slim",false),
            bounded(config.getDouble("spawn-distance",2.5),1,6),
            bounded(config.getDouble("spawn-search-radius",8),6,16),
            boundedInt(config.getInt("effects.departure-delay-ticks",60),0,200),
            bounded(config.getDouble("movement.arrival-distance",3),0.5,6),
            bounded(config.getDouble("movement.speed",0.9),0.2,2.5),
            wait,
            resume,
            boundedInt(config.getInt("idle-timeout-seconds",600),30,3600),
            chat,
            speech,
            List.copyOf(config.getStringList("messages.waiting")),
            config.contains("messages.scanning")
                ? List.copyOf(config.getStringList("messages.scanning")) : DEFAULT_SCANNING_MESSAGES,
            boundedInt(config.getInt("messages.scanning-cooldown-seconds",10),1,3600),
            List.copyOf(config.getStringList("messages.stuck")),
            List.copyOf(config.getStringList("messages.farewell")),
            Map.copyOf(actions),
            Map.copyOf(commandWorld),
            Map.copyOf(firstTimeWorld)
        );
    }

    private static Map<String,List<Instruction>> readActions(ConfigSection section) {
        if(section==null) return Map.of();

        Map<String,List<Instruction>> result=new LinkedHashMap<>();
        for(String name:section.getKeys(false)) {
            List<?> raw=section.getList(name);
            List<Instruction> program=new ArrayList<>();

            for(Object item:raw) {
                if(!(item instanceof String line))
                    throw new IllegalArgumentException("actions."+name+" entries must be strings");
                program.add(parseInstruction(line));
            }

            result.put(name,List.copyOf(program));
        }
        return result;
    }

    private static Map<String,String> readRoutes(ConfigSection section) {
        if(section==null) return Map.of();

        Map<String,String> result=new LinkedHashMap<>();
        for(String world:section.getKeys(false)) {
            String action=section.getString(world,"").trim();
            if(!action.isBlank())
                result.put(world.toLowerCase(Locale.ROOT),action);
        }
        return result;
    }

    static Instruction parseInstruction(String raw) {
        String line=raw.trim();
        if(line.isBlank())
            throw new IllegalArgumentException("STEMBot action instruction cannot be blank");

        int colon=line.indexOf(':');
        String command=(colon<0?line:line.substring(0,colon)).trim().toLowerCase(Locale.ROOT);
        String argument=colon<0?"":line.substring(colon+1).trim();

        return switch(command) {
            case "say" -> new Instruction(Op.SAY,required(argument,"say text"),0,0,0,0,0,List.of());
            case "talk" -> new Instruction(Op.TALK,required(argument,"talk text"),0,0,0,0,0,List.of());
            case "sleep" -> new Instruction(Op.SLEEP,"",0,0,0,0,
                boundedInt(parseInt(argument,"sleep"),0,24000),List.of());
            case "walk" -> parsePointInstruction(Op.WALK,argument,true);
            case "look" -> argument.equalsIgnoreCase("player")
                ?new Instruction(Op.LOOK,"player",0,0,0,0,0,List.of())
                :parsePointInstruction(Op.LOOK,argument,false);
            case "point" -> argument.equalsIgnoreCase("player")
                ?new Instruction(Op.POINT,"player",0,0,0,0,0,List.of())
                :parsePointInstruction(Op.POINT,argument,false);
            case "speed" -> new Instruction(Op.SPEED,"",0,0,0,
                bounded(parseDouble(argument,"speed"),0.2,2.5),0,List.of());
            case "wave" -> Instruction.simple(Op.WAVE);
            case "sneak" -> Instruction.simple(Op.SNEAK);
            case "stand" -> Instruction.simple(Op.STAND);
            case "action" -> new Instruction(Op.ACTION,required(argument,"action name"),0,0,0,0,0,List.of());
            case "await" -> parseAwait(argument);
            case "listen" -> new Instruction(Op.LISTEN,"",0,0,0,0,0,parseListen(argument));
            case "end" -> Instruction.simple(Op.END);
            case "close" -> Instruction.simple(Op.CLOSE);
            default -> throw new IllegalArgumentException("Unknown STEMBot instruction: "+command);
        };
    }

    private static Instruction parseAwait(String argument) {
        String[] parts = argument.split("\\s*->\\s*", 2);
        String[] request = parts[0].trim().split("\\s+");
        if (parts.length != 2 || request.length != 2 || !request[0].matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))
            throw new IllegalArgumentException("await expects namespace:key seconds -> success-action, skip-action");
        String[] targets = parts[1].split("\\s*,\\s*");
        if (targets.length != 2 || targets[0].isBlank() || targets[1].isBlank())
            throw new IllegalArgumentException("await requires success and skip actions");
        int seconds = parseInt(request[1], "await seconds");
        if (seconds < 1 || seconds > 300) throw new IllegalArgumentException("await timeout must be 1..300 seconds");
        return new Instruction(Op.AWAIT, request[0], 0, 0, 0, 0, seconds * 20,
            List.of(new ListenRoute(List.of(), targets[0]),
                new ListenRoute(List.of(wildcard("skip"), wildcard("later"), wildcard("not now")), targets[1])));
    }

    private static Instruction parsePointInstruction(Op op,String argument,boolean optionalSpeed) {
        String[] parts=argument.trim().split("\\s+");
        int min=3,max=optionalSpeed?4:3;
        if(parts.length<min||parts.length>max)
            throw new IllegalArgumentException(op.name().toLowerCase(Locale.ROOT)
                +" expects x y z"+(optionalSpeed?" [speed]":""));

        double x=parseDouble(parts[0],op.name());
        double y=parseDouble(parts[1],op.name());
        double z=parseDouble(parts[2],op.name());
        double speed=parts.length==4?bounded(parseDouble(parts[3],"walk speed"),0.2,2.5):0;

        return new Instruction(op,"",x,y,z,speed,0,List.of());
    }

    static List<ListenRoute> parseListen(String argument) {
        if(argument.isBlank())
            throw new IllegalArgumentException("listen requires at least one route");

        List<ListenRoute> routes=new ArrayList<>();
        for(String branch:argument.split("\\s*,\\s*")) {
            String[] pair=branch.split("\\s*->\\s*",2);
            if(pair.length!=2||pair[0].isBlank()||pair[1].isBlank())
                throw new IllegalArgumentException(
                    "listen routes must use 'pattern -> action'");

            List<Pattern> patterns=new ArrayList<>();
            for(String alternative:pair[0].split("\\|")) {
                String value=alternative.trim();
                if(value.isBlank()) continue;
                patterns.add(wildcard(value));
            }

            if(patterns.isEmpty())
                throw new IllegalArgumentException("listen route has no patterns");

            routes.add(new ListenRoute(List.copyOf(patterns),pair[1].trim()));
        }
        return List.copyOf(routes);
    }

    private static Pattern wildcard(String value) {
        if(value.equals("*"))
            return Pattern.compile(".*",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);

        StringBuilder regex=new StringBuilder("^");
        int start=0;
        for(int i=0;i<value.length();i++) {
            if(value.charAt(i)!='*') continue;
            regex.append(Pattern.quote(value.substring(start,i))).append(".*");
            start=i+1;
        }
        regex.append(Pattern.quote(value.substring(start))).append('$');

        return Pattern.compile(regex.toString(),Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    }

    private static void validateTargets(
        Map<String,List<Instruction>> actions,
        Map<String,String> commandWorld,
        Map<String,String> firstTimeWorld
    ) {
        for(var entry:actions.entrySet()) {
            for(Instruction instruction:entry.getValue()) {
                if(instruction.op()==Op.ACTION)
                    requireTarget(actions,instruction.text(),"actions."+entry.getKey());

                if(instruction.op()==Op.LISTEN || instruction.op()==Op.AWAIT)
                    for(ListenRoute route:instruction.routes())
                        if(instruction.op()==Op.AWAIT || (!route.target().equalsIgnoreCase("close")
                            &&!route.target().equalsIgnoreCase("end")))
                            requireTarget(actions,route.target(),"listen in "+entry.getKey());
            }
        }

        commandWorld.forEach((world,target)->requireTarget(actions,target,"command.world."+world));
        firstTimeWorld.forEach((world,target)->requireTarget(actions,target,"first-time.world."+world));
    }

    private static void requireTarget(Map<String,List<Instruction>> actions,String target,String source) {
        if(!actions.containsKey(target))
            throw new IllegalArgumentException(source+" references unknown action '"+target+"'");
    }

    private static String required(String value,String what) {
        if(value.isBlank()) throw new IllegalArgumentException(what+" cannot be blank");
        return value;
    }

    private static int parseInt(String value, @SuppressWarnings("SameParameterValue") String name) {
        try {
            return Integer.parseInt(value.trim());
        } catch(NumberFormatException ex) {
            throw new IllegalArgumentException(name+" must be an integer",ex);
        }
    }

    private static double parseDouble(String value,String name) {
        try {
            return Double.parseDouble(value.trim());
        } catch(NumberFormatException ex) {
            throw new IllegalArgumentException(name+" must be numeric",ex);
        }
    }

    private static double bounded(double value,double min,double max) {
        if(!Double.isFinite(value)||value<min||value>max)
            throw new IllegalArgumentException("STEMBot setting outside "+min+".."+max);
        return value;
    }

    private static int boundedInt(int value,int min,int max) {
        if(value<min||value>max)
            throw new IllegalArgumentException("STEMBot setting outside "+min+".."+max);
        return value;
    }
}
