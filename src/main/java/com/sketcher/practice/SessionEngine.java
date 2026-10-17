package com.sketcher.practice;

import com.sketcher.library.LibraryService;
import com.sketcher.model.Segment;
import com.sketcher.model.TickBehavior;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives a practice session over a flattened queue of (segment, imageId)
 * pairs. Owns the timer/tick-behavior state machine; the UI calls tick()
 * once a second and reacts to listener callbacks.
 *
 * Memory-drawing mode (PLAN.md 4.4) runs its own three-phase cycle per image
 * inside this state machine: SHOWING for memoryShowSeconds, then
 * MEMORY_HIDDEN (blank canvas) for memoryHideSeconds, then MEMORY_REVEALED
 * (image back, held until dismissed). A segment's normal secondsPerImage /
 * tickBehavior are ignored while memoryMode is on, since the show/hide
 * seconds govern timing instead.
 *
 * Per-segment timer ramping interpolates secondsPerImage -> rampToSeconds
 * linearly across a segment's images, keyed off each image's position
 * within its own segment (not the whole session).
 */
public class SessionEngine {

    public enum Phase { SHOWING, MEMORY_HIDDEN, MEMORY_REVEALED, BREAK, WAITING_FOR_CLICK, FINISHED }

    public interface Listener {
        void onImageChanged(int imageId, Segment segment, int positionInSession, int totalInSession);

        void onPhaseChanged(Phase phase, int secondsRemaining);

        void onSessionFinished();
    }

    private record FlatItem(int imageId, Segment segment, int indexInSegment, int segmentSize) {
    }

    private final LibraryService library;
    private final List<FlatItem> queue = new ArrayList<>();
    private final Integer presetId;
    private Listener listener = NO_OP;

    private int cursor = -1;
    private Phase phase = Phase.FINISHED;
    private int remainingSeconds;
    private boolean paused;
    private Integer sessionId;
    private long currentImageStartedAt;
    private int distinctImagesShown;

    private static final Listener NO_OP = new Listener() {
        public void onImageChanged(int imageId, Segment segment, int position, int total) {
        }

        public void onPhaseChanged(Phase phase, int secondsRemaining) {
        }

        public void onSessionFinished() {
        }
    };

    public SessionEngine(LibraryService library, List<ResolvedSegment> resolved, Integer presetId) {
        this.library = library;
        this.presetId = presetId;
        for (ResolvedSegment rs : resolved) {
            int size = rs.imageIds().size();
            for (int i = 0; i < size; i++) {
                queue.add(new FlatItem(rs.imageIds().get(i), rs.segment(), i, size));
            }
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener == null ? NO_OP : listener;
    }

    public int totalCount() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void start() {
        sessionId = library.views().startSession(presetId, System.currentTimeMillis());
        distinctImagesShown = 0;
        cursor = -1;
        if (!advanceCursor(1)) {
            finish();
            return;
        }
        beginShowingCurrent();
    }

    public void tick() {
        if (paused || phase == Phase.FINISHED) return;
        switch (phase) {
            case SHOWING -> {
                Segment seg = current().segment();
                if (seg.isMemoryMode()) {
                    remainingSeconds--;
                    if (remainingSeconds <= 0) {
                        enterMemoryHidden(seg);
                    } else {
                        listener.onPhaseChanged(phase, remainingSeconds);
                    }
                    return;
                }
                if (!seg.isTimed()) return;
                remainingSeconds--;
                if (remainingSeconds <= 0) {
                    handleTimerExpired(seg);
                } else {
                    listener.onPhaseChanged(phase, remainingSeconds);
                }
            }
            case MEMORY_HIDDEN -> {
                remainingSeconds--;
                if (remainingSeconds <= 0) {
                    phase = Phase.MEMORY_REVEALED;
                    listener.onPhaseChanged(phase, 0);
                } else {
                    listener.onPhaseChanged(phase, remainingSeconds);
                }
            }
            case BREAK -> {
                remainingSeconds--;
                if (remainingSeconds <= 0) {
                    goTo(cursor + 1);
                } else {
                    listener.onPhaseChanged(phase, remainingSeconds);
                }
            }
            default -> {
            }
        }
    }

    public void advanceClick() {
        if (phase == Phase.WAITING_FOR_CLICK || phase == Phase.MEMORY_REVEALED) {
            goTo(cursor + 1);
        }
    }

    public void next() {
        goTo(cursor + 1);
    }

    public void previous() {
        if (cursor > 0) {
            goTo(cursor - 1);
        }
    }

    public void togglePause() {
        if (phase == Phase.FINISHED) return;
        paused = !paused;
        listener.onPhaseChanged(phase, remainingSeconds);
    }

    public boolean isPaused() {
        return paused;
    }

    public Phase phase() {
        return phase;
    }

    public void end() {
        recordCurrentView();
        if (sessionId != null) {
            library.views().endSession(sessionId, System.currentTimeMillis(), distinctImagesShown);
        }
        phase = Phase.FINISHED;
    }

    public Integer currentImageId() {
        FlatItem item = current();
        return item == null ? null : item.imageId();
    }

    public Segment currentSegment() {
        FlatItem item = current();
        return item == null ? null : item.segment();
    }

    public int positionInSession() {
        return cursor;
    }

    private FlatItem current() {
        return (cursor >= 0 && cursor < queue.size()) ? queue.get(cursor) : null;
    }

    private void goTo(int newCursor) {
        if (newCursor < 0) return;
        recordCurrentView();
        if (newCursor >= queue.size()) {
            finish();
            return;
        }
        cursor = newCursor;
        beginShowingCurrent();
    }

    private boolean advanceCursor(int delta) {
        int target = cursor + delta;
        if (target < 0 || target >= queue.size()) return false;
        cursor = target;
        return true;
    }

    private void beginShowingCurrent() {
        FlatItem item = current();
        currentImageStartedAt = System.currentTimeMillis();
        paused = false;
        distinctImagesShown++;
        Segment seg = item.segment();
        remainingSeconds = seg.isMemoryMode() ? seg.memoryShowSeconds() : effectiveSecondsForItem(item);
        phase = Phase.SHOWING;
        listener.onImageChanged(item.imageId(), seg, cursor, queue.size());
        listener.onPhaseChanged(phase, remainingSeconds);
    }

    private void enterMemoryHidden(Segment seg) {
        phase = Phase.MEMORY_HIDDEN;
        remainingSeconds = seg.memoryHideSeconds();
        listener.onPhaseChanged(phase, remainingSeconds);
    }

    private void handleTimerExpired(Segment seg) {
        switch (seg.tickBehavior()) {
            case ADVANCE_IMMEDIATELY -> goTo(cursor + 1);
            case BREAK -> {
                phase = Phase.BREAK;
                remainingSeconds = seg.breakSeconds();
                listener.onPhaseChanged(phase, remainingSeconds);
            }
            case WAIT_FOR_CLICK -> {
                phase = Phase.WAITING_FOR_CLICK;
                listener.onPhaseChanged(phase, 0);
            }
        }
    }

    /** Interpolates secondsPerImage -> rampToSeconds across this item's position within its own segment. */
    private int effectiveSecondsForItem(FlatItem item) {
        Segment seg = item.segment();
        if (!seg.isRamped() || item.segmentSize() <= 1) return seg.secondsPerImage();
        double t = (double) item.indexInSegment() / (item.segmentSize() - 1);
        return Math.round(seg.secondsPerImage() + (float) (t * (seg.rampToSeconds() - seg.secondsPerImage())));
    }

    private void recordCurrentView() {
        FlatItem item = current();
        if (item == null) return;
        long shownMs = System.currentTimeMillis() - currentImageStartedAt;
        library.images().recordView(item.imageId(), sessionId, System.currentTimeMillis(), shownMs);
    }

    private void finish() {
        if (sessionId != null) {
            library.views().endSession(sessionId, System.currentTimeMillis(), distinctImagesShown);
        }
        phase = Phase.FINISHED;
        listener.onSessionFinished();
    }
}
