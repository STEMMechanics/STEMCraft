package dev.stemcraft.feature.quest;

import java.util.UUID;

/** Active quest progress for one player. */
public final class QuestProgress {
    public enum State { ACTIVE, READY }

    private final UUID playerUuid;
    private final String questId;
    private int objectiveIndex;
    private int objectiveProgress;
    private State state;

    public QuestProgress(UUID playerUuid, String questId, int objectiveIndex, int objectiveProgress, State state) {
        this.playerUuid = playerUuid;
        this.questId = questId;
        this.objectiveIndex = objectiveIndex;
        this.objectiveProgress = objectiveProgress;
        this.state = state;
    }

    public UUID playerUuid() { return playerUuid; }
    public String questId() { return questId; }
    public int objectiveIndex() { return objectiveIndex; }
    public void objectiveIndex(int value) { objectiveIndex = value; }
    public int objectiveProgress() { return objectiveProgress; }
    public void objectiveProgress(int value) { objectiveProgress = value; }
    public State state() { return state; }
    public void state(State value) { state = value; }
}
