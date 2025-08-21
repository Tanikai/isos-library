package isos.benchmark.application.model;

import java.io.Serializable;

public record KVCommand(KVCommandType commandType, String key, String data)
    implements Serializable {}
