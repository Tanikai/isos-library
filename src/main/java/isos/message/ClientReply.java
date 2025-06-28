package isos.message;

/**
 * Interface used for objects that are returned from the replica to the client. The contents should
 * be the same. Metadata such as from which replica the reply came from should be sent via the
 * {@link isos.communication.ClientMessageWrapper}.
 */
public interface ClientReply {}
