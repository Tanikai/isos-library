package isos.message;

import isos.consensus.DependencySet;
import isos.consensus.SequenceNumber;

public record ExecuteMessage(
        SequenceNumber seqNum,
        ClientRequest clientRequest,
        DependencySet depSet
) {
}
