package isos.message.replica.fast;

import isos.consensus.model.DependencySet;
import isos.consensus.model.SequenceNumber;
import isos.message.replica.ISOSMessage;
import isos.message.replica.ISOSMessageType;
import isos.utils.ReplicaId;
import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @param seqNum agreement slot
 * @param followerId Follower ID
 * @param depProposeHash Hash of the DepPropose msg that this DepVerify refers to
 * @param depSet dependency set determined by follower
 */
public record DepVerifyMessage(
    SequenceNumber seqNum, ReplicaId followerId, String depProposeHash, DependencySet depSet)
    implements ISOSMessage, Serializable {

  @Override
  public ISOSMessageType msgType() {
    return ISOSMessageType.DEP_VERIFY;
  }

  @Override
  public ReplicaId logicalSender() {
    return this.followerId;
  }

  /**
   * @param depVerifies
   * @param depPropose
   * @return
   */
  public static DependencySet unionOfDependencies(
      List<DepVerifyMessage> depVerifies, DepProposeMessage depPropose) {
    var allDeps =
        depVerifies.stream()
            .flatMap(m -> m.depSet().dependencies().stream())
            .collect(Collectors.toSet());
    if (depPropose != null) {
      allDeps.addAll(depPropose.depSet().dependencies());
    }
    return new DependencySet(allDeps);
  }

  public static List<DepVerifyMessage> sortDepVerifys(List<DepVerifyMessage> input) {
    return input.stream().sorted(Comparator.comparingInt(msg -> msg.followerId().value())).toList();
  }
}
