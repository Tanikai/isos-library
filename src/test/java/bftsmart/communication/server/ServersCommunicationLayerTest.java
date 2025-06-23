package bftsmart.communication.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import bftsmart.communication.SystemMessage;
import bftsmart.configuration.ConfigurationManager;
import isos.utils.ReplicaId;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
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
    assertTrue(replicas.contains(new ReplicaId(1)));
    assertTrue(replicas.contains(new ReplicaId(2)));
    assertTrue(replicas.contains(new ReplicaId(3)));
  }

  @Test
  public void testGetAllConnectedReplicasExcludeSelf() {
    List<ReplicaId> replicas = commLayer.getAllConnectedReplicas(false);
    assertFalse(replicas.contains(new ReplicaId(1)));
    assertTrue(replicas.contains(new ReplicaId(2)));
    assertTrue(replicas.contains(new ReplicaId(3)));
  }
}
