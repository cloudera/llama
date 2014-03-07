package com.cloudera.llama.am.impl;

import com.cloudera.llama.am.api.LlamaAM;
import com.cloudera.llama.am.api.Resource;
import com.cloudera.llama.am.api.TestUtils;
import com.cloudera.llama.am.spi.RMConnector;
import com.cloudera.llama.am.spi.RMResource;
import com.cloudera.llama.util.LlamaException;
import junit.framework.TestCase;
import org.apache.hadoop.conf.Configuration;
import org.junit.Assert;
import org.junit.Before;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class TestPhasingOutRMConnector extends TestCase {
  class MyRMConnector extends RecordingMockRMConnector {
    private Collection<RMResource> reserved = new ArrayList<RMResource>();

    @Override
    public synchronized void reserve(Collection<RMResource> resources)
        throws LlamaException {
      super.reserve(resources);
      this.reserved.addAll(resources);
    }

    @Override
    public synchronized void release(Collection<RMResource> resources, boolean doNotCache)
        throws LlamaException {
      super.release(resources, doNotCache);
      this.reserved.removeAll(resources);
    }

    @Override
    public synchronized boolean hasResources() {
      return reserved.size() != 0;
    }
  }
  private PhasingOutRMConnector rmConnector;

  @Before
  public void setUp() throws LlamaException {
    Configuration conf = new Configuration();
    ScheduledExecutorService stp = Mockito.mock(ScheduledExecutorService.class);

    rmConnector = new PhasingOutRMConnector(conf, stp,
        new PhasingOutRMConnector.RmConnectorCreator() {
          @Override
          public RMConnector create() {
            return new MyRMConnector();
          }
    });
    rmConnector.setRMListener(null);
    rmConnector.setMetricRegistry(null);
  }

  public void testSimpleReserveAndAllocate() throws Exception {
    RMResource request = TestUtils.createRMResource("node1", Resource
        .Locality.MUST, 3,
        3000);
    List<RMResource> reservations = Arrays.asList(request);
    rmConnector.reserve(reservations);

    // Only the first one should be there.
    RMConnector[] connectors = rmConnector.getConnectors();
    Assert.assertEquals(connectors.length, 1);
    MyRMConnector originalRmConnector =
        (MyRMConnector) connectors[0];
    Assert.assertTrue(originalRmConnector.reserved.equals(reservations));

    // Force the runnable so that it phases out the active one.
    rmConnector.run();

    // Now there should be two of the connectors.
    connectors = rmConnector.getConnectors();
    Assert.assertEquals(connectors.length, 2);

    // Active should not have any calls.
    MyRMConnector activeMockRMConnector =
        (MyRMConnector) connectors[0];
    Assert.assertEquals(activeMockRMConnector.reserved.size(), 0);

    // Phased out should have the previous reservations.
    MyRMConnector phasedOutMockRMConnector =
        (MyRMConnector) connectors[1];
    Assert.assertTrue(phasedOutMockRMConnector.reserved.equals(reservations));

    // Test the previous is same as the original rm.
    Assert.assertEquals(originalRmConnector, phasedOutMockRMConnector);

    // Release the previous reservation.
    rmConnector.release(reservations, true);
    Assert.assertEquals(phasedOutMockRMConnector.reserved.size(), 0);

    // Issue a new reservation.
    rmConnector.reserve(reservations);

    // Only the new one should have the reservation
    // and old should have anything new.
    Assert.assertTrue(activeMockRMConnector.reserved.equals(reservations));
    Assert.assertEquals(phasedOutMockRMConnector.reserved.size(), 0);

    rmConnector.release(reservations, true);
    Assert.assertEquals(activeMockRMConnector.reserved.size(), 0);

    // Force the runnable again so that it phases out the active one.
    rmConnector.run();

    connectors = rmConnector.getConnectors();
    Assert.assertEquals(connectors.length, 1);
    Assert.assertTrue(!connectors[0].equals(activeMockRMConnector));
    Assert.assertTrue(!connectors[0].equals(phasedOutMockRMConnector));

    // are two new connectors and the old one should have been stopped.
    Assert.assertEquals("stop", activeMockRMConnector.
        invoked.get(activeMockRMConnector.invoked.size()-1));
    Assert.assertEquals("stop", phasedOutMockRMConnector.
        invoked.get(phasedOutMockRMConnector.invoked.size()-1));
  }
}
