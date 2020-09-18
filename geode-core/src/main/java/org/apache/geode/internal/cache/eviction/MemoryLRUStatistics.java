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
package org.apache.geode.internal.cache.eviction;

import org.apache.geode.StatisticDescriptor;
import org.apache.geode.Statistics;
import org.apache.geode.StatisticsFactory;
import org.apache.geode.StatisticsType;
import org.apache.geode.StatisticsTypeFactory;
import org.apache.geode.annotations.Immutable;
import org.apache.geode.internal.statistics.StatisticsTypeFactoryImpl;

public class MemoryLRUStatistics implements EvictionStats {
  @Immutable
  private static final StatisticsType statType;
  private static final int limitId;
  private static final int counterId;
  private static final int albertoCounterId;
  private static final int evictionsId;
  private static final int destroysId;
  private static final int evaluationsId;
  private static final int greedyReturnsId;

  static {
    StatisticsTypeFactory f = StatisticsTypeFactoryImpl.singleton();

    final String bytesAllowedDesc = "Number of total bytes allowed in this region.";
    final String byteCountDesc = "Number of bytes in region.";
    final String albertoCountDesc = "Number of alberto bytes in region.";
    final String lruEvictionsDesc = "Number of total entry evictions triggered by LRU.";
    final String lruDestroysDesc =
        "Number of entries destroyed in the region through both destroy cache operations and eviction.";
    final String lruEvaluationsDesc = "Number of entries evaluated during LRU operations.";
    final String lruGreedyReturnsDesc = "Number of non-LRU entries evicted during LRU operations";

    statType = f.createType("MemLRUStatistics", "Statistics relates to memory based eviction",
        new StatisticDescriptor[] {f.createLongGauge("bytesAllowed", bytesAllowedDesc, "bytes"),
            f.createLongGauge("byteCount", byteCountDesc, "bytes"),
            f.createLongGauge("albertoCount", byteCountDesc, "bytes"),
            f.createLongCounter("lruEvictions", lruEvictionsDesc, "entries"),
            f.createLongCounter("lruDestroys", lruDestroysDesc, "entries"),
            f.createLongCounter("lruEvaluations", lruEvaluationsDesc, "entries"),
            f.createLongCounter("lruGreedyReturns", lruGreedyReturnsDesc, "entries")});

    limitId = statType.nameToId("bytesAllowed");
    counterId = statType.nameToId("byteCount");
    albertoCounterId = statType.nameToId("albertoCount");
    evictionsId = statType.nameToId("lruEvictions");
    destroysId = statType.nameToId("lruDestroys");
    evaluationsId = statType.nameToId("lruEvaluations");
    greedyReturnsId = statType.nameToId("lruGreedyReturns");
  }

  private final Statistics stats;

  public MemoryLRUStatistics(StatisticsFactory factory, String name) {
    this.stats = factory.createAtomicStatistics(statType, "MemLRUStatistics-" + name);
  }

  @Override
  public Statistics getStatistics() {
    return this.stats;
  }

  @Override
  public void close() {
    this.stats.close();
  }

  @Override
  public void incEvictions() {
    this.stats.incLong(evictionsId, 1);
  }

  @Override
  public void updateCounter(long delta) {
    this.stats.incLong(counterId, delta);
  }

  @Override
  public void updateAlbertoCounter(long delta) {
    this.stats.incLong(albertoCounterId, delta);
  }

  @Override
  public void incDestroys() {
    this.stats.incLong(destroysId, 1);
  }

  @Override
  public void setLimit(long newValue) {
    this.stats.setLong(limitId, newValue);
  }

  @Override
  public void setCounter(long newValue) {
    this.stats.setLong(counterId, newValue);
  }

  @Override
  public void incEvaluations(long delta) {
    this.stats.incLong(evaluationsId, delta);
  }

  @Override
  public void incGreedyReturns(long delta) {
    this.stats.incLong(greedyReturnsId, delta);
  }

}
