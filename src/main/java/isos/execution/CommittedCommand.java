package isos.execution;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.message.client.OrderedClientRequest;
import isos.message.replica.ClientRequestContainer;

/**
 * A data structure that holds information about a client command that is committed and can be
 * forwarded to execution.
 *
 * @param seqNum The sequence number of the committed command.
 * @param clientRequest The application-specific request of the application.
 * @param depSet The dependency set of the command, determined in parts by ISOS (e.g., same client)
 *     and the user defined application.
 */
public record CommittedCommand(
        SequenceNumber seqNum, ClientRequestContainer clientRequest, DependencySet depSet) {}
