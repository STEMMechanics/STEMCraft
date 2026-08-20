package dev.stemcraft.feature.quest;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Editable quest definition persisted in quests.yml. */
public final class QuestDefinition {
    private final String id;
    private String title;
    private String author = "STEMCraft";
    private String description = "A mysterious task awaits.";
    private String shortDescription;
    private String rewardText = "A reward awaits on completion.";
    private boolean enabled = true;
    private boolean repeatable;
    private long timeLimitSeconds;
    private long restartCooldownSeconds;
    private int globalMaxCompletions;
    private UUID startNpc;
    private UUID endNpc;
    private String startNpcProfile;
    private String endNpcProfile;
    private String startNpcName = "Quest Giver";
    private String endNpcName = "Quest Contact";
    private final Map<String, List<String>> dialogue = new LinkedHashMap<>();
    private final Set<String> requirements = new LinkedHashSet<>();
    private final List<QuestObjective> objectives = new ArrayList<>();
    private final List<String> rewardCommands = new ArrayList<>();
    private final List<QuestRewardItem> rewardItems = new ArrayList<>();

    public QuestDefinition(String id, String title) {
        this.id = id;
        this.title = title;
        dialogue.put("offer", new ArrayList<>(List.of("I have a task that may interest you.")));
        dialogue.put("idle", new ArrayList<>(List.of("Safe travels.", "Fine weather for an adventure.")));
        dialogue.put("incomplete", new ArrayList<>(List.of("You do not have everything I need yet.")));
        dialogue.put("objective", new ArrayList<>(List.of("Ah, I was expecting you.")));
        dialogue.put("complete", new ArrayList<>(List.of("Excellent work. You have earned this reward.")));
    }

    public String id() { return id; }
    public String title() { return title; }
    public void title(String value) { title = value; }
    public String author() { return author; }
    public void author(String value) { author = value; }
    public String description() { return description; }
    public void description(String value) { description = value; }
    public String shortDescription() { return shortDescription == null || shortDescription.isBlank() ? description : shortDescription; }
    public void shortDescription(String value) { shortDescription = value; }
    public String rewardText() { return rewardText; }
    public void rewardText(String value) { rewardText = value; }
    public boolean enabled() { return enabled; }
    public void enabled(boolean value) { enabled = value; }
    public boolean repeatable() { return repeatable; }
    public void repeatable(boolean value) { repeatable = value; }
    public long timeLimitSeconds() { return timeLimitSeconds; }
    public void timeLimitSeconds(long value) { timeLimitSeconds = Math.max(0, value); }
    public long restartCooldownSeconds() { return restartCooldownSeconds; }
    public void restartCooldownSeconds(long value) { restartCooldownSeconds = Math.max(0, value); }
    public int globalMaxCompletions() { return globalMaxCompletions; }
    public void globalMaxCompletions(int value) { globalMaxCompletions = Math.max(0, value); }
    public @Nullable UUID startNpc() { return startNpc; }
    public void startNpc(@Nullable UUID value) { startNpc = value; }
    public @Nullable UUID endNpc() { return endNpc; }
    public void endNpc(@Nullable UUID value) { endNpc = value; }
    public @Nullable String startNpcProfile() { return startNpcProfile; }
    public void startNpcProfile(@Nullable String value) { startNpcProfile = value; }
    public @Nullable String endNpcProfile() { return endNpcProfile; }
    public void endNpcProfile(@Nullable String value) { endNpcProfile = value; }
    public String startNpcName() { return startNpcName; }
    public void startNpcName(String value) { startNpcName = value; }
    public String endNpcName() { return endNpcName; }
    public void endNpcName(String value) { endNpcName = value; }
    public Map<String, List<String>> dialogue() { return dialogue; }
    public List<String> dialogue(String state) { return dialogue.computeIfAbsent(state, ignored -> new ArrayList<>()); }
    public Set<String> requirements() { return requirements; }
    public List<QuestObjective> objectives() { return objectives; }
    public List<String> rewardCommands() { return rewardCommands; }
    public List<QuestRewardItem> rewardItems() { return rewardItems; }
}
