package isos.consensus.model;

import isos.consensus.buffer.ReplicaMessageAlreadyPresent;
import isos.message.replica.viewchange.ViewChangeMessage;
import isos.utils.ReplicaId;
import isos.utils.ViewNumber;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ViewChangeMap {
  private final Map<ViewNumber, Map<ReplicaId, ViewChangeMessage>> viewChanges;

  public ViewChangeMap() {
    this.viewChanges = new HashMap<>();
  }

  public void setViewChange(ViewChangeMessage viewChange) throws ReplicaMessageAlreadyPresent {
    var sender = viewChange.replicaId();
    var viewNumber = viewChange.viewNumber();

    var viewNumberMap = this.viewChanges.computeIfAbsent(viewNumber, x -> new HashMap<>());

    if (viewNumberMap.containsKey(sender)) {
      throw new ReplicaMessageAlreadyPresent(sender, viewNumber, viewChange.msgType());
    }

    viewNumberMap.put(sender, viewChange);
  }

  public boolean reachedQuorum(ViewNumber viewNumber, int quorumSize) {
    return this.viewChanges.computeIfAbsent(viewNumber, x -> new HashMap<>()).size() >= quorumSize;
  }

  public Map<ReplicaId, ViewChangeMessage> getViewChanges(ViewNumber viewNumber) {
    return Collections.unmodifiableMap(this.viewChanges.computeIfAbsent(viewNumber, x -> new HashMap<>()));
  }
}
