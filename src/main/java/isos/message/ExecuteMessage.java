package isos.message;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;

public record ExecuteMessage(
        SequenceNumber seqNum,
        ClientRequest clientRequest,
        DependencySet depSet
) {
}
