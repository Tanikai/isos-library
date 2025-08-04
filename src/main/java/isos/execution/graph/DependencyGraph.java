package isos.execution.graph;

import isos.consensus.model.SequenceNumber;

import java.util.Set;

public record DependencyGraph(Set<SequenceNumber> slots, Set<Dependency> edges) {}
