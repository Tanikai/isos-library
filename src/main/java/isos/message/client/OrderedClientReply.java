package isos.message.client;

import java.io.Serializable;

public record OrderedClientReply(byte[] response) implements ClientReply, Serializable  {}
