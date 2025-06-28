package isos.message;

import java.io.Serializable;

public record OrderedClientReply(byte[] response) implements ClientReply, Serializable  {}
