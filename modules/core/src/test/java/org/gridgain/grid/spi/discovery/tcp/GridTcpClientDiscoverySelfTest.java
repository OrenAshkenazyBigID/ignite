/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.discovery.tcp.ipfinder.*;
import org.gridgain.grid.spi.discovery.tcp.ipfinder.vm.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.testframework.junits.common.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.util.concurrent.TimeUnit.*;
import static org.gridgain.grid.events.GridEventType.*;

/**
 * Client-based discovery tests.
 */
public class GridTcpClientDiscoverySelfTest extends GridCommonAbstractTest {
    /** */
    private static final GridTcpDiscoveryIpFinder IP_FINDER = new GridTcpDiscoveryVmIpFinder(true);

    /** */
    private static final AtomicInteger srvIdx = new AtomicInteger();

    /** */
    private static final AtomicInteger clientIdx = new AtomicInteger();

    /** */
    private static Collection<UUID> srvNodeIds;

    /** */
    private static Collection<UUID> clientNodeIds;

    /** */
    private static CountDownLatch srvJoinLatch;

    /** */
    private static CountDownLatch srvLeftLatch;

    /** */
    private static CountDownLatch srvFailedLatch;

    /** */
    private static CountDownLatch clientJoinLatch;

    /** */
    private static CountDownLatch clientLeftLatch;

    /** */
    private static CountDownLatch clientFailedLatch;

    /** {@inheritDoc} */
    @Override protected GridConfiguration getConfiguration(String gridName) throws Exception {
        GridConfiguration cfg = super.getConfiguration(gridName);

        cfg.setLocalHost("127.0.0.1");

        if (gridName.startsWith("server")) {
            GridTcpDiscoverySpi disco = new GridTcpDiscoverySpi();

            disco.setIpFinder(IP_FINDER);

            cfg.setDiscoverySpi(disco);
        }
        else if (gridName.startsWith("client")) {
            GridTcpClientDiscoverySpi disco = new GridTcpClientDiscoverySpi();

            GridTcpDiscoveryVmIpFinder ipFinder = new GridTcpDiscoveryVmIpFinder();

            String addr = new ArrayList<>(IP_FINDER.getRegisteredAddresses()).get(clientIdx.get() / 2).toString();

            ipFinder.setAddresses(Arrays.asList(addr));

            disco.setIpFinder(IP_FINDER);

            cfg.setDiscoverySpi(disco);
        }

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        srvIdx.set(0);
        clientIdx.set(0);

        srvNodeIds = new GridConcurrentHashSet<>();
        clientNodeIds = new GridConcurrentHashSet<>();
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        stopAllGrids();
    }

    /**
     * @throws Exception If failed.
     */
    public void testClientNodeJoin() throws Exception {
        startServerNodes(3);
        startClientNodes(3);

        checkNodes(3, 3);

        srvJoinLatch = new CountDownLatch(3);
        clientJoinLatch = new CountDownLatch(3);

        attachListeners(3, 3);

        startClientNodes(1);

        await(srvJoinLatch);
        await(clientJoinLatch);

        checkNodes(3, 4);
    }

    /**
     * @throws Exception If failed.
     */
    public void testClientNodeLeave() throws Exception {
        startServerNodes(3);
        startClientNodes(3);

        checkNodes(3, 3);

        srvLeftLatch = new CountDownLatch(3);
        clientLeftLatch = new CountDownLatch(2);

        attachListeners(3, 3);

        stopGrid("client-2");

        await(srvLeftLatch);
        await(clientLeftLatch);

        checkNodes(3, 2);
    }

    /**
     * @throws Exception If failed.
     */
    public void testClientNodeFail() throws Exception {
        startServerNodes(3);
        startClientNodes(3);

        checkNodes(3, 3);

        srvFailedLatch = new CountDownLatch(3);
        clientFailedLatch = new CountDownLatch(2);

        attachListeners(3, 3);

        ((GridTcpClientDiscoverySpi)G.grid("client-2").configuration().getDiscoverySpi()).simulateNodeFailure();

        await(srvFailedLatch);
        await(clientFailedLatch);

        checkNodes(3, 2);
    }

    /**
     * @throws Exception If failed.
     */
    public void testServerNodeJoin() throws Exception {
        startServerNodes(3);
        startClientNodes(3);

        checkNodes(3, 3);

        srvJoinLatch = new CountDownLatch(3);
        clientJoinLatch = new CountDownLatch(3);

        attachListeners(3, 3);

        startServerNodes(1);

        await(srvJoinLatch);
        await(clientJoinLatch);

        checkNodes(4, 3);
    }

    /**
     * @throws Exception If failed.
     */
    public void testServerNodeLeave() throws Exception {
        startServerNodes(3);
        startClientNodes(3);

        checkNodes(3, 3);

        srvLeftLatch = new CountDownLatch(2);
        clientLeftLatch = new CountDownLatch(3);

        attachListeners(3, 3);

        stopGrid("server-2");

        await(srvLeftLatch);
        await(clientLeftLatch);

        checkNodes(2, 3);
    }

    /**
     * @throws Exception If failed.
     */
    public void testServerNodeFail() throws Exception {
        startServerNodes(3);
        startClientNodes(3);

        checkNodes(3, 3);

        srvFailedLatch = new CountDownLatch(2);
        clientFailedLatch = new CountDownLatch(3);

        attachListeners(3, 3);

        ((GridTcpDiscoverySpi)G.grid("server-2").configuration().getDiscoverySpi()).simulateNodeFailure();

        await(srvFailedLatch);
        await(clientFailedLatch);

        checkNodes(2, 3);
    }

    /**
     * @param cnt Number of nodes.
     * @throws Exception In case of error.
     */
    private void startServerNodes(int cnt) throws Exception {
        for (int i = 0; i < cnt; i++) {
            Grid g = startGrid("server-" + srvIdx.getAndIncrement());

            srvNodeIds.add(g.localNode().id());
        }
    }

    /**
     * @param cnt Number of nodes.
     * @throws Exception In case of error.
     */
    private void startClientNodes(int cnt) throws Exception {
        for (int i = 0; i < cnt; i++) {
            Grid g = startGrid("client-" + clientIdx.getAndIncrement());

            clientNodeIds.add(g.localNode().id());
        }
    }

    /**
     * @param srvCnt Number of server nodes.
     * @param clientCnt Number of client nodes.
     */
    private void attachListeners(int srvCnt, int clientCnt) throws Exception {
        if (srvJoinLatch != null) {
            for (int i = 0; i < srvCnt; i++) {
                G.grid("server-" + i).events().localListen(new GridPredicate<GridEvent>() {
                    @Override public boolean apply(GridEvent evt) {
                        info("Event fired: " + evt);

                        srvJoinLatch.countDown();

                        return true;
                    }
                }, EVT_NODE_JOINED);
            }
        }

        if (srvLeftLatch != null) {
            for (int i = 0; i < srvCnt; i++) {
                G.grid("server-" + i).events().localListen(new GridPredicate<GridEvent>() {
                    @Override public boolean apply(GridEvent evt) {
                        info("Event fired: " + evt);

                        srvLeftLatch.countDown();

                        return true;
                    }
                }, EVT_NODE_LEFT);
            }
        }

        if (srvFailedLatch != null) {
            for (int i = 0; i < srvCnt; i++) {
                G.grid("server-" + i).events().localListen(new GridPredicate<GridEvent>() {
                    @Override public boolean apply(GridEvent evt) {
                        info("Event fired: " + evt);

                        srvFailedLatch.countDown();

                        return true;
                    }
                }, EVT_NODE_FAILED);
            }
        }

        if (clientJoinLatch != null) {
            for (int i = 0; i < clientCnt; i++) {
                G.grid("client-" + i).events().localListen(new GridPredicate<GridEvent>() {
                    @Override public boolean apply(GridEvent evt) {
                        info("Event fired: " + evt);

                        clientJoinLatch.countDown();

                        return true;
                    }
                }, EVT_NODE_JOINED);
            }
        }

        if (clientLeftLatch != null) {
            for (int i = 0; i < clientCnt; i++) {
                G.grid("client-" + i).events().localListen(new GridPredicate<GridEvent>() {
                    @Override public boolean apply(GridEvent evt) {
                        info("Event fired: " + evt);

                        clientLeftLatch.countDown();

                        return true;
                    }
                }, EVT_NODE_LEFT);
            }
        }

        if (clientFailedLatch != null) {
            for (int i = 0; i < clientCnt; i++) {
                G.grid("client-" + i).events().localListen(new GridPredicate<GridEvent>() {
                    @Override public boolean apply(GridEvent evt) {
                        info("Event fired: " + evt);

                        clientFailedLatch.countDown();

                        return true;
                    }
                }, EVT_NODE_FAILED);
            }
        }
    }

    /**
     * @param srvCnt Number of server nodes.
     * @param clientCnt Number of client nodes.
     */
    private void checkNodes(int srvCnt, int clientCnt) {
        for (int i = 0; i < srvCnt; i++) {
            Grid g = G.grid("server-" + i);

            assertTrue(srvNodeIds.contains(g.localNode().id()));

            assertFalse(g.localNode().isClient());

            checkRemoteNodes(g, srvCnt + clientCnt - 1);
        }

        for (int i = 0; i < clientCnt; i++) {
            Grid g = G.grid("client-" + i);

            assertTrue(clientNodeIds.contains(g.localNode().id()));

            assertTrue(g.localNode().isClient());

            checkRemoteNodes(g, srvCnt + clientCnt - 1);
        }
    }

    /**
     * @param grid Grid.
     * @param expCnt Expected nodes count.
     */
    @SuppressWarnings("TypeMayBeWeakened")
    private void checkRemoteNodes(Grid grid, int expCnt) {
        Collection<GridNode> nodes = grid.forRemotes().nodes();

        assertEquals(expCnt, nodes.size());

        for (GridNode node : nodes) {
            UUID id = node.id();

            if (clientNodeIds.contains(id))
                assertTrue(node.isClient());
            else if (srvNodeIds.contains(id))
                assertFalse(node.isClient());
            else
                assert false : "Unexpected node ID: " + id;
        }
    }

    /**
     * @param latch Latch.
     * @throws InterruptedException If interrupted.
     */
    private void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(5000, MILLISECONDS));
    }
}
