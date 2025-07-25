package isos.execution;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.message.client.ClientRequest;

public record ExecuteMessage(
        SequenceNumber seqNum,
        ClientRequest clientRequest,
        DependencySet depSet
) {
}
