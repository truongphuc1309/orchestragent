package com.orchestragent.agent.domain;

import java.util.List;
import java.util.OptionalLong;

public interface TaskMessageRepository {

    /**
     * Stores the messages of a task, skipping any whose number is already stored.
     *
     * @return how many were new
     */
    int append(TaskId taskId, List<TaskMessage> messages);

    /** Messages with a number above {@code afterSeq} (use -1 for all), in order, at most {@code limit}. */
    List<TaskMessage> findAfter(TaskId taskId, long afterSeq, int limit);

    /** The highest stored number, or empty when the task has no messages. */
    OptionalLong lastSeq(TaskId taskId);
}
