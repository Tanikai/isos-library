package isos.execution;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.message.client.OrderedClientRequest;


public record CommittedCommand(
    SequenceNumber seqNum, OrderedClientRequest clientRequest, DependencySet depSet) {}
