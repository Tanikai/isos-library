package isos.execution.graph;

import isos.consensus.model.SequenceNumber;

/**
 * There is no Pair<K,V> type in the Java standard library, so we create our own Dependency pair
 * type.
 *
 * @param from
 * @param to
 */
public record Dependency(SequenceNumber from, SequenceNumber to) {}
