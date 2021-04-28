/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.management.internal.cli.functions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.junit.Before;
import org.junit.Test;

import org.apache.geode.distributed.internal.DistributionManager;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.internal.monitoring.ThreadsMonitoring;

public class ReplicateRegionFunctionTest {

  private ReplicateRegionFunction rrf;
  private long startTime;
  private final int batchSize = 25;
  private final int entries = batchSize;
  private Clock clockMock;
  private ReplicateRegionFunction.ThreadSleeper threadSleeperMock;
  private InternalCache cacheMock;
  private ThreadsMonitoring threadsMonitoringMock;

  @Before
  public void setUp() throws InterruptedException {
    clockMock = mock(Clock.class);
    threadSleeperMock = mock(ReplicateRegionFunction.ThreadSleeper.class);
    doNothing().when(threadSleeperMock).millis(anyLong());
    rrf = new ReplicateRegionFunction();
    rrf.setClock(clockMock);
    rrf.setThreadSleeper(threadSleeperMock);
    startTime = System.currentTimeMillis();
    cacheMock = mock(InternalCache.class);
    threadsMonitoringMock = mock(ThreadsMonitoring.class);
    doNothing().when(threadsMonitoringMock).updateThreadStatus();
    DistributionManager dmMock = mock(DistributionManager.class);
    when(dmMock.getThreadMonitoring()).thenReturn(threadsMonitoringMock);
    when(cacheMock.getDistributionManager()).thenReturn(dmMock);
  }

  @Test
  public void doActionsIfBatchReplicated_DoNothingIfBatchIsIncomplete()
      throws InterruptedException {
    rrf.doActionsIfBatchReplicated(cacheMock, startTime, 5, 20, 1L);
    verify(threadSleeperMock, never()).millis(anyLong());
    verify(threadsMonitoringMock, never()).updateThreadStatus();
  }

  @Test
  public void doActionsIfBatchReplicated_DoNotSleepAndUpdateThreadStatusIfBatchIsCompleteAndMaxRateIsZero()
      throws InterruptedException {
    rrf.doActionsIfBatchReplicated(cacheMock, startTime, 20, 20, 0);
    verify(threadSleeperMock, never()).millis(anyLong());
    verify(threadsMonitoringMock, times(1)).updateThreadStatus();
  }

  @Test
  public void doActionsIfBatchReplicated_SleepAndUpdateThreadStatusIfElapsedTimeIsZero()
      throws InterruptedException {
    long maxRate = 100;
    long elapsedTime = 0L;
    long expectedMsToSleep = 250L;
    when(clockMock.millis()).thenReturn(startTime + elapsedTime);
    rrf.doActionsIfBatchReplicated(cacheMock, startTime, entries, batchSize, maxRate);
    verify(threadSleeperMock, times(1)).millis(expectedMsToSleep);
    verify(threadsMonitoringMock, times(1)).updateThreadStatus();
  }

  @Test
  public void doActionsIfBatchReplicated_DoNotSleepAndUpdateThreadStatusIfMaxRateNotReached()
      throws InterruptedException {
    long maxRate = 10000;
    long elapsedTime = 100L;
    when(clockMock.millis()).thenReturn(startTime + elapsedTime);
    rrf.doActionsIfBatchReplicated(cacheMock, startTime, entries, batchSize, maxRate);
    verify(threadSleeperMock, never()).millis(anyLong());
    verify(threadsMonitoringMock, times(1)).updateThreadStatus();
  }

  @Test
  public void doActionsIfBatchReplicated_SleepAndUpdateThreadStatusIfMaxRateReached()
      throws InterruptedException {
    long maxRate = 100;
    long elapsedTime = 100L;
    long expectedMsToSleep = 150L;
    when(clockMock.millis()).thenReturn(startTime + elapsedTime);
    rrf.doActionsIfBatchReplicated(cacheMock, startTime, entries, batchSize, maxRate);
    verify(threadSleeperMock, times(1)).millis(expectedMsToSleep);
    verify(threadsMonitoringMock, times(1)).updateThreadStatus();
  }

  @Test
  public void doActionsIfBatchReplicated_ThrowInterruptedIfInterruptedAndBatchCompleted() {
    long maxRate = 100;
    Thread.currentThread().interrupt();
    assertThatThrownBy(
        () -> rrf.doActionsIfBatchReplicated(cacheMock, startTime, entries, batchSize, maxRate))
            .isInstanceOf(InterruptedException.class);
  }

  @Test
  public void doActionsIfBatchReplicated_DoNotThrowInterruptedIfInterruptedAndBatchNotCompleted()
      throws InterruptedException {
    long maxRate = 100;
    Thread.currentThread().interrupt();
    rrf.doActionsIfBatchReplicated(cacheMock, startTime, entries - 1, batchSize, maxRate);
  }
}
