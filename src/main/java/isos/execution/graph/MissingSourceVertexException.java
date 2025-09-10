package isos.execution.graph;

public class MissingSourceVertexException extends RuntimeException {
  public MissingSourceVertexException(Dependency edge) {
    super(String.format("Graph doesn't contain source vertex for edge %s->%s", edge.from(), edge.to()));
  }
}
