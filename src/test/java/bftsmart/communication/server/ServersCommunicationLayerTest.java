package bftsmart.communication.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import bftsmart.communication.SystemMessage;
import bftsmart.configuration.ConfigurationManager;
import isos.utils.ReplicaId;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ServersCommunicationLayerTest {

  private ServersCommunicationLayer commLayer;
  private ConfigurationManager configManager;
  private LinkedBlockingQueue<SystemMessage> inQueue;

  @BeforeEach
  public void setUp() throws Exception {
    configManager = mock(ConfigurationManager.class, RETURNS_DEEP_STUBS);
    inQueue = new LinkedBlockingQueue<>();
    when(configManager.getStaticConf().getProcessId()).thenReturn(1);
    when(configManager.getStaticConf().getSSLTLSProtocolVersion()).thenReturn("TLSv1.2");
    when(configManager.getStaticConf().getSSLTLSKeyStore()).thenReturn("testKeystore");
    when(configManager.getStaticConf().getBindAddress()).thenReturn("");
    when(configManager.getStaticConf().getServerToServerPort(anyInt())).thenReturn(12345);
    when(configManager.getStaticConf().getEnabledCiphers())
        .thenReturn(new String[] {"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"});

    commLayer = spy(new ServersCommunicationLayer(configManager, inQueue));

    // Simulate connections
    Field connectionsField = ServersCommunicationLayer.class.getDeclaredField("connections");
    connectionsField.setAccessible(true);
    HashMap<Integer, ServerConnection> connections = new HashMap<>();
    connections.put(2, mock(ServerConnection.class));
    connections.put(3, mock(ServerConnection.class));
    connectionsField.set(commLayer, connections);
  }

  @Test
  public void testGetAllConnectedReplicasIncludeSelf() {
    List<ReplicaId> replicas = commLayer.getAllConnectedReplicas(true);
    assertTrue(replicas.contains(ReplicaId.of(1)));
    assertTrue(replicas.contains(ReplicaId.of(2)));
    assertTrue(replicas.contains(ReplicaId.of(3)));
  }

  @Test
  public void testGetAllConnectedReplicasExcludeSelf() {
    List<ReplicaId> replicas = commLayer.getAllConnectedReplicas(false);
    assertFalse(replicas.contains(ReplicaId.of(1)));
    assertTrue(replicas.contains(ReplicaId.of(2)));
    assertTrue(replicas.contains(ReplicaId.of(3)));
  }

  @Test
  public void testGetLowestPingReplicas() {
    Map<Integer, ServerConnection> connections = new HashMap<>();
    ServerConnection conn1 = mock(ServerConnection.class);
    when(conn1.getCurrentPingMillis()).thenReturn(50L);
    ServerConnection conn2 = mock(ServerConnection.class);
    when(conn2.getCurrentPingMillis()).thenReturn(10L);
    ServerConnection conn3 = mock(ServerConnection.class);
    when(conn3.getCurrentPingMillis()).thenReturn(30L);
    ServerConnection conn4 = mock(ServerConnection.class);
    when(conn4.getCurrentPingMillis()).thenReturn(20L);
    connections.put(1, conn1);
    connections.put(2, conn2);
    connections.put(3, conn3);
    connections.put(4, conn4);

    Set<ReplicaId> result = ServersCommunicationLayer.getLowestPingReplicas(connections, 2);
    Set<ReplicaId> expected = new HashSet<>(Arrays.asList(ReplicaId.of(2), ReplicaId.of(4)));
    // The two lowest pings are 10 (id=2) and 20 (id=4)
    assertEquals(expected, result);
  }

  @Test
  public void testGetLowestPingReplicasNotEnoughReplicas() {
    Map<Integer, ServerConnection> connections = new HashMap<>();
    connections.put(1, mock(ServerConnection.class));
    assertThrows(
        IllegalStateException.class,
        () -> {
          ServersCommunicationLayer.getLowestPingReplicas(connections, 2);
        });
  }
}
