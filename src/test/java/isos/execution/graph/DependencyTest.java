package isos.execution.graph;

import isos.consensus.model.SequenceNumber;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.SequencedCollection;

import static org.junit.jupiter.api.Assertions.*;

class DependencyTest {


  @Test
  void testSameDependencyInSet() {
    var depSet = new HashSet<Dependency>();

    depSet.add(new Dependency(new SequenceNumber(0,0), new SequenceNumber(1,1)));
    depSet.add(new Dependency(new SequenceNumber(0,0), new SequenceNumber(1,1)));

    assertEquals(1, depSet.size());
  }

}
