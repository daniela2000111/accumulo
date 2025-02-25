/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.accumulo.core.fate.zookeeper;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.accumulo.core.fate.zookeeper.ZooCache.ZcStat;
import org.apache.accumulo.core.fate.zookeeper.ZooCache.ZooCacheWatcher;
import org.apache.accumulo.core.zookeeper.ZooSession;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ZooCacheTest {
  private static final String ZPATH = "/some/path/in/zk";
  private static final byte[] DATA = {(byte) 1, (byte) 2, (byte) 3, (byte) 4};
  private static final List<String> CHILDREN = java.util.Arrays.asList("huey", "dewey", "louie");

  private ZooSession zk;
  private ZooCache zc;

  @BeforeEach
  public void setUp() {
    zk = createStrictMock(ZooSession.class);
    zc = new ZooCache(zk);
  }

  @Test
  public void testGet() throws Exception {
    testGet(false);
  }

  @Test
  public void testGet_FillStat() throws Exception {
    testGet(true);
  }

  private void testGet(boolean fillStat) throws Exception {
    ZcStat myStat = null;
    if (fillStat) {
      myStat = new ZcStat();
    }
    final long ephemeralOwner = 123456789L;
    Stat existsStat = new Stat();
    existsStat.setEphemeralOwner(ephemeralOwner);
    expect(zk.exists(eq(ZPATH), anyObject(Watcher.class))).andReturn(existsStat);
    expect(zk.getData(eq(ZPATH), anyObject(Watcher.class), eq(existsStat))).andReturn(DATA);
    replay(zk);

    assertFalse(zc.dataCached(ZPATH));
    assertArrayEquals(DATA, (fillStat ? zc.get(ZPATH, myStat) : zc.get(ZPATH)));
    verify(zk);
    if (fillStat) {
      assertEquals(ephemeralOwner, myStat.getEphemeralOwner());
    }

    assertTrue(zc.dataCached(ZPATH));
    assertSame(DATA, zc.get(ZPATH)); // cache hit
  }

  @Test
  public void testGet_NonExistent() throws Exception {
    expect(zk.exists(eq(ZPATH), anyObject(Watcher.class))).andReturn(null);
    replay(zk);

    assertNull(zc.get(ZPATH));
    verify(zk);
  }

  @Test
  public void testGet_Retry_NoNode() throws Exception {
    testGet_Retry(new KeeperException.NoNodeException(ZPATH));
  }

  @Test
  public void testGet_Retry_ConnectionLoss() throws Exception {
    testGet_Retry(new KeeperException.ConnectionLossException());
  }

  @Test
  public void testGet_Retry_BadVersion() throws Exception {
    testGet_Retry(new KeeperException.BadVersionException(ZPATH));
  }

  @Test
  public void testGet_Retry_Interrupted() throws Exception {
    testGet_Retry(new InterruptedException());
  }

  private void testGet_Retry(Exception e) throws Exception {
    expect(zk.exists(eq(ZPATH), anyObject(Watcher.class))).andThrow(e);
    Stat existsStat = new Stat();
    expect(zk.exists(eq(ZPATH), anyObject(Watcher.class))).andReturn(existsStat);
    expect(zk.getData(eq(ZPATH), anyObject(Watcher.class), eq(existsStat))).andReturn(DATA);
    replay(zk);

    assertArrayEquals(DATA, zc.get(ZPATH));
    verify(zk);
  }

  @Test
  public void testGet_Retry2_NoNode() throws Exception {
    testGet_Retry2(new KeeperException.NoNodeException(ZPATH));
  }

  @Test
  public void testGet_Retry2_ConnectionLoss() throws Exception {
    testGet_Retry2(new KeeperException.ConnectionLossException());
  }

  @Test
  public void testGet_Retry2_BadVersion() throws Exception {
    testGet_Retry2(new KeeperException.BadVersionException(ZPATH));
  }

  @Test
  public void testGet_Retry2_Interrupted() throws Exception {
    testGet_Retry2(new InterruptedException());
  }

  private void testGet_Retry2(Exception e) throws Exception {
    Stat existsStat = new Stat();
    expect(zk.exists(eq(ZPATH), anyObject(Watcher.class))).andReturn(existsStat);
    expect(zk.getData(eq(ZPATH), anyObject(Watcher.class), eq(existsStat))).andThrow(e);
    expect(zk.exists(eq(ZPATH), anyObject(Watcher.class))).andReturn(existsStat);
    expect(zk.getData(eq(ZPATH), anyObject(Watcher.class), eq(existsStat))).andReturn(DATA);
    replay(zk);

    assertArrayEquals(DATA, zc.get(ZPATH));
    verify(zk);
  }

  // ---

  @Test
  public void testGetChildren() throws Exception {
    Stat existsStat = new Stat();
    expect(zk.exists(eq(ZPATH), anyObject(Watcher.class))).andReturn(existsStat);
    expect(zk.getChildren(eq(ZPATH), anyObject(Watcher.class))).andReturn(CHILDREN);
    replay(zk);

    assertFalse(zc.childrenCached(ZPATH));
    assertEquals(CHILDREN, zc.getChildren(ZPATH));
    verify(zk);

    assertTrue(zc.childrenCached(ZPATH));
    // cannot check for sameness, return value is wrapped each time
    assertEquals(CHILDREN, zc.getChildren(ZPATH)); // cache hit
  }

  @Test
  public void testGetChildren_NoKids() throws Exception {
    Stat existsStat = new Stat();
    expect(zk.exists(eq(ZPATH), anyObject(Watcher.class))).andReturn(existsStat);
    expect(zk.getChildren(eq(ZPATH), anyObject(Watcher.class))).andReturn(List.of());
    replay(zk);

    assertEquals(List.of(), zc.getChildren(ZPATH));
    verify(zk);

    assertEquals(List.of(), zc.getChildren(ZPATH)); // cache hit
  }

  @Test
  public void testGetChildren_RaceCondition() throws Exception {
    // simulate the node being deleted between calling zookeeper.exists and zookeeper.getChildren
    Stat existsStat = new Stat();
    expect(zk.exists(eq(ZPATH), anyObject(Watcher.class))).andReturn(existsStat);
    expect(zk.getChildren(eq(ZPATH), anyObject(Watcher.class)))
        .andThrow(new KeeperException.NoNodeException(ZPATH));
    expect(zk.exists(eq(ZPATH), anyObject(Watcher.class))).andReturn(null);
    replay(zk);
    assertNull(zc.getChildren(ZPATH));
    verify(zk);
    assertNull(zc.getChildren(ZPATH));
  }

  @Test
  public void testGetChildren_Retry() throws Exception {
    Stat existsStat = new Stat();
    expect(zk.exists(eq(ZPATH), anyObject(Watcher.class))).andReturn(existsStat);
    expect(zk.getChildren(eq(ZPATH), anyObject(Watcher.class)))
        .andThrow(new KeeperException.BadVersionException(ZPATH));
    expect(zk.exists(eq(ZPATH), anyObject(Watcher.class))).andReturn(existsStat);
    expect(zk.getChildren(eq(ZPATH), anyObject(Watcher.class))).andReturn(CHILDREN);
    replay(zk);

    assertEquals(CHILDREN, zc.getChildren(ZPATH));
    verify(zk);
    assertEquals(CHILDREN, zc.getChildren(ZPATH));
  }

  @Test
  public void testGetChildren_NoNode() throws Exception {
    assertFalse(zc.childrenCached(ZPATH));
    assertFalse(zc.dataCached(ZPATH));
    expect(zk.exists(eq(ZPATH), anyObject(Watcher.class))).andReturn(null);
    replay(zk);

    assertNull(zc.getChildren(ZPATH));
    verify(zk);
    assertNull(zc.getChildren(ZPATH));
    // when its discovered a node does not exists in getChildren then its also known it does not
    // exists for getData
    assertNull(zc.get(ZPATH));
    assertTrue(zc.childrenCached(ZPATH));
    assertTrue(zc.dataCached(ZPATH));
  }

  private static class TestWatcher implements ZooCacheWatcher {
    private final WatchedEvent expectedEvent;
    private boolean wasCalled;

    TestWatcher(WatchedEvent event) {
      expectedEvent = event;
      wasCalled = false;
    }

    @Override
    public void accept(WatchedEvent event) {
      assertSame(expectedEvent, event);
      wasCalled = true;
    }

    boolean wasCalled() {
      return wasCalled;
    }
  }

  @Test
  public void testWatchDataNode_Deleted() throws Exception {
    testWatchDataNode(DATA, Watcher.Event.EventType.NodeDeleted, false);
  }

  @Test
  public void testWatchDataNode_DataChanged() throws Exception {
    testWatchDataNode(DATA, Watcher.Event.EventType.NodeDataChanged, false);
  }

  @Test
  public void testWatchDataNode_Created() throws Exception {
    testWatchDataNode(null, Watcher.Event.EventType.NodeCreated, false);
  }

  @Test
  public void testWatchDataNode_NoneSyncConnected() throws Exception {
    testWatchDataNode(null, Watcher.Event.EventType.None, true);
  }

  private void testWatchDataNode(byte[] initialData, Watcher.Event.EventType eventType,
      boolean stillCached) throws Exception {
    WatchedEvent event =
        new WatchedEvent(eventType, Watcher.Event.KeeperState.SyncConnected, ZPATH);
    TestWatcher exw = new TestWatcher(event);
    zc = new ZooCache(zk, exw);

    Watcher w = watchData(initialData);
    w.process(event);
    assertTrue(exw.wasCalled());
    assertEquals(stillCached, zc.dataCached(ZPATH));
  }

  private Watcher watchData(byte[] initialData) throws Exception {
    Capture<Watcher> cw = EasyMock.newCapture();
    Stat existsStat = new Stat();
    if (initialData != null) {
      expect(zk.exists(eq(ZPATH), capture(cw))).andReturn(existsStat);
      expect(zk.getData(eq(ZPATH), anyObject(Watcher.class), eq(existsStat)))
          .andReturn(initialData);
    } else {
      expect(zk.exists(eq(ZPATH), capture(cw))).andReturn(null);
    }
    replay(zk);
    zc.get(ZPATH);
    assertTrue(zc.dataCached(ZPATH));

    return cw.getValue();
  }

  @Test
  public void testWatchDataNode_Disconnected() throws Exception {
    testWatchDataNode_Clear(Watcher.Event.KeeperState.Disconnected);
  }

  @Test
  public void testWatchDataNode_Expired() throws Exception {
    testWatchDataNode_Clear(Watcher.Event.KeeperState.Expired);
  }

  @Test
  public void testGetDataThenChildren() throws Exception {
    testGetBoth(true);
  }

  @Test
  public void testGetChildrenThenDate() throws Exception {
    testGetBoth(false);
  }

  private void testGetBoth(boolean getDataFirst) throws Exception {
    assertFalse(zc.childrenCached(ZPATH));
    assertFalse(zc.dataCached(ZPATH));

    var uc1 = zc.getUpdateCount();

    final long ephemeralOwner1 = 123456789L;
    Stat existsStat1 = new Stat();
    existsStat1.setEphemeralOwner(ephemeralOwner1);

    final long ephemeralOwner2 = 987654321L;
    Stat existsStat2 = new Stat();
    existsStat2.setEphemeralOwner(ephemeralOwner2);

    if (getDataFirst) {
      expect(zk.exists(eq(ZPATH), anyObject(Watcher.class))).andReturn(existsStat1);
      expect(zk.getData(eq(ZPATH), anyObject(Watcher.class), eq(existsStat1))).andReturn(DATA);
      expect(zk.exists(eq(ZPATH), anyObject(Watcher.class))).andReturn(existsStat2);
      expect(zk.getChildren(eq(ZPATH), anyObject(Watcher.class))).andReturn(CHILDREN);
    } else {
      expect(zk.exists(eq(ZPATH), anyObject(Watcher.class))).andReturn(existsStat2);
      expect(zk.getChildren(eq(ZPATH), anyObject(Watcher.class))).andReturn(CHILDREN);
      expect(zk.exists(eq(ZPATH), anyObject(Watcher.class))).andReturn(existsStat1);
      expect(zk.getData(eq(ZPATH), anyObject(Watcher.class), eq(existsStat1))).andReturn(DATA);
    }

    replay(zk);

    if (getDataFirst) {
      var zcStat = new ZcStat();
      var data = zc.get(ZPATH, zcStat);
      assertEquals(ephemeralOwner1, zcStat.getEphemeralOwner());
      assertArrayEquals(DATA, data);
    } else {
      var children = zc.getChildren(ZPATH);
      assertEquals(CHILDREN, children);
    }
    var uc2 = zc.getUpdateCount();
    assertTrue(uc1 < uc2);

    if (getDataFirst) {
      var children = zc.getChildren(ZPATH);
      assertEquals(CHILDREN, children);
    } else {
      var zcStat = new ZcStat();
      var data = zc.get(ZPATH, zcStat);
      assertEquals(ephemeralOwner1, zcStat.getEphemeralOwner());
      assertArrayEquals(DATA, data);
    }
    var uc3 = zc.getUpdateCount();
    assertTrue(uc2 < uc3);

    verify(zk);

    var zcStat = new ZcStat();
    var data = zc.get(ZPATH, zcStat);
    // the stat is associated with the data so should aways see the one returned by the call to get
    // data and not get children
    assertEquals(ephemeralOwner1, zcStat.getEphemeralOwner());
    assertArrayEquals(DATA, data);
    var children = zc.getChildren(ZPATH);
    assertEquals(CHILDREN, children);
    // everything is cached so the get calls on the cache should not change the update count
    assertEquals(uc3, zc.getUpdateCount());
  }

  private void testWatchDataNode_Clear(Watcher.Event.KeeperState state) throws Exception {
    WatchedEvent event = new WatchedEvent(Watcher.Event.EventType.None, state, null);
    TestWatcher exw = new TestWatcher(event);
    zc = new ZooCache(zk, exw);

    Watcher w = watchData(DATA);
    assertTrue(zc.dataCached(ZPATH));
    w.process(event);
    assertTrue(exw.wasCalled());
    assertFalse(zc.dataCached(ZPATH));
  }

  @Test
  public void testWatchChildrenNode_Deleted() throws Exception {
    testWatchChildrenNode(CHILDREN, Watcher.Event.EventType.NodeDeleted, false);
  }

  @Test
  public void testWatchChildrenNode_ChildrenChanged() throws Exception {
    testWatchChildrenNode(CHILDREN, Watcher.Event.EventType.NodeChildrenChanged, false);
  }

  @Test
  public void testWatchChildrenNode_Created() throws Exception {
    testWatchChildrenNode(null, Watcher.Event.EventType.NodeCreated, false);
  }

  @Test
  public void testWatchChildrenNode_NoneSyncConnected() throws Exception {
    testWatchChildrenNode(CHILDREN, Watcher.Event.EventType.None, true);
  }

  private void testWatchChildrenNode(List<String> initialChildren,
      Watcher.Event.EventType eventType, boolean stillCached) throws Exception {
    WatchedEvent event =
        new WatchedEvent(eventType, Watcher.Event.KeeperState.SyncConnected, ZPATH);
    TestWatcher exw = new TestWatcher(event);
    zc = new ZooCache(zk, exw);

    Watcher w = watchChildren(initialChildren);
    w.process(event);
    assertTrue(exw.wasCalled());
    assertEquals(stillCached, zc.childrenCached(ZPATH));
  }

  private Watcher watchChildren(List<String> initialChildren) throws Exception {
    Capture<Watcher> cw = EasyMock.newCapture();
    if (initialChildren == null) {
      expect(zk.exists(eq(ZPATH), capture(cw))).andReturn(null);
    } else {
      Stat existsStat = new Stat();
      expect(zk.exists(eq(ZPATH), anyObject(Watcher.class))).andReturn(existsStat);
      expect(zk.getChildren(eq(ZPATH), capture(cw))).andReturn(initialChildren);
    }
    replay(zk);
    zc.getChildren(ZPATH);
    assertTrue(zc.childrenCached(ZPATH));

    return cw.getValue();
  }
}
