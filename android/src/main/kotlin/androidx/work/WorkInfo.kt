package androidx.work

import java.util.UUID

class WorkInfo(
    val id: UUID,
    val state: State,
    val outputData: Data = Data.EMPTY,
    val tags: Set<String> = emptySet(),
    val runAttemptCount: Int = 0
) {
    enum class State {
        ENQUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        BLOCKED,
        CANCELLED;

        val isFinished: Boolean
            get() = this == SUCCEEDED || this == FAILED || this == CANCELLED
    }
}
