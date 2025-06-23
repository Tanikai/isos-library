package isos.consensus;


import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Requirement: DependencySet has to be sent to other replicas. */
public class DependencySet implements Externalizable {
  private Set<SequenceNumber> depSet;

  public DependencySet() {
    // https://stackoverflow.com/questions/48442/rule-of-thumb-for-choosing-an-implementation-of-a-java-collection
    this.depSet = new HashSet<>();
  }

  public DependencySet(Set<SequenceNumber> depSet) {
    this.depSet = new HashSet<>(depSet); // we want to have a hashset
  }

  public DependencySet(List<SequenceNumber> depSetList) {
    this.depSet = new HashSet<>(depSetList);
  }

  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
    out.writeInt(depSet.size());
    for (SequenceNumber sn : depSet) {
      out.writeObject(sn);
    }
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    int size = in.readInt();
    if (size < 0 || size > 1000000) { // Arbitrary upper bound for safety
      throw new IOException("DependencySet size is invalid or too large: " + size);
    }
    depSet = new HashSet<>(size);
    for (int i = 0; i < size; i++) {
      Object obj = in.readObject();
      if (!(obj instanceof SequenceNumber)) {
        throw new IOException("Invalid SequenceNumber in DependencySet");
      }
      depSet.add((SequenceNumber) obj);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    DependencySet other = (DependencySet) obj;
    return depSet.equals(other.depSet);
  }
}
