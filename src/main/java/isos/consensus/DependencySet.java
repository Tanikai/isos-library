package isos.consensus;

import isos.utils.NotImplementedException;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashSet;
import java.util.Set;

/**
 * Requirement: DependencySet has to be sent to other replicas.
 */
public class DependencySet implements Externalizable {
  private Set<SequenceNumber> depSet;

  public DependencySet() {
    // https://stackoverflow.com/questions/48442/rule-of-thumb-for-choosing-an-implementation-of-a-java-collection
    this.depSet = new HashSet<>();
  }

  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
    throw new NotImplementedException();
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    // TODO: Needs to be protected against huge dependency sets
    throw new NotImplementedException();
  }
}
