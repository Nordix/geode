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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.junit.Before;
import org.junit.Test;

public class ReplicateRegionFunctionTest {

  private ReplicateRegionFunction rrf;
  private long startTime;
  private int batchSize = 25;
  private int entries = batchSize;
  private Clock clockMock;
  private ReplicateRegionFunction.ThreadSleeper threadSleeperMock;

  @Before
  public void setUp() throws InterruptedException {
    clockMock = mock(Clock.class);
    threadSleeperMock = mock(ReplicateRegionFunction.ThreadSleeper.class);
    doNothing().when(threadSleeperMock).millis(anyLong());
    rrf = new ReplicateRegionFunction();
    rrf.setClock(clockMock);
    rrf.setThreadSleeper(threadSleeperMock);
    startTime = System.currentTimeMillis();
  }

  @Test
  public void doActionsIfBatchReplicated_ReturnsFalseIfBatchIsIncomplete()
      throws InterruptedException {
    assertThat(rrf.doActionsIfBatchReplicated(startTime, 5, 20, 1L)).isFalse();
  }

  @Test
  public void doActionsIfBatchReplicated_ReturnsTrueIfBatchIsCompleteAndMaxRateIsZero()
      throws InterruptedException {
    assertThat(rrf.doActionsIfBatchReplicated(startTime, 20, 20, 0)).isTrue();
  }

  @Test
  public void doActionsIfBatchReplicated_DoNotSleepIfElapsedTimeIsZero()
      throws InterruptedException {
    long maxRate = 100;
    long elapsedTime = 0L;
    long expectedMsToSleep = 250L;
    when(clockMock.millis()).thenReturn(startTime + elapsedTime);
    assertThat(rrf.doActionsIfBatchReplicated(startTime, entries, batchSize, maxRate)).isTrue();
    verify(threadSleeperMock, times(1)).millis(expectedMsToSleep);
  }

  @Test
  public void doActionsIfBatchReplicated_DoNotSleepIfMaxRateNotReached()
      throws InterruptedException {
    long maxRate = 10000;
    long elapsedTime = 100L;
    when(clockMock.millis()).thenReturn(startTime + elapsedTime);
    assertThat(rrf.doActionsIfBatchReplicated(startTime, entries, batchSize, maxRate)).isTrue();
    verify(threadSleeperMock, times(0)).millis(anyLong());
  }

  @Test
  public void doActionsIfBatchReplicated_SleepIfMaxRateReached() throws InterruptedException {
    long maxRate = 100;
    long elapsedTime = 100L;
    long expectedMsToSleep = 150L;
    when(clockMock.millis()).thenReturn(startTime + elapsedTime);
    assertThat(rrf.doActionsIfBatchReplicated(startTime, entries, batchSize, maxRate)).isTrue();
    verify(threadSleeperMock, times(1)).millis(expectedMsToSleep);
  }
}
