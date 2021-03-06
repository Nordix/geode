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
 *
 */

package org.apache.geode.redis.internal.data;



import java.util.ArrayList;
import java.util.List;

import org.apache.geode.cache.Region;
import org.apache.geode.redis.internal.executor.sortedset.SortedSetRangeOptions;
import org.apache.geode.redis.internal.executor.sortedset.ZAddOptions;

class NullRedisSortedSet extends RedisSortedSet {

  NullRedisSortedSet() {
    super(new ArrayList<>());
  }

  @Override
  public boolean isNull() {
    return true;
  }

  @Override
  Object zadd(Region<RedisKey, RedisData> region, RedisKey key, List<byte[]> membersToAdd,
      ZAddOptions options) {
    if (options.isXX()) {
      if (options.isINCR()) {
        return null;
      }
      return 0;
    }

    if (options.isINCR()) {
      return zaddIncr(region, key, membersToAdd);
    }

    RedisSortedSet sortedSet = new RedisSortedSet(membersToAdd);
    region.create(key, sortedSet);

    return sortedSet.getSortedSetSize();
  }

  @Override
  long zcount(SortedSetRangeOptions rangeOptions) {
    return 0;
  }

  @Override
  byte[] zincrby(Region<RedisKey, RedisData> region, RedisKey key, byte[] increment,
      byte[] member) {
    List<byte[]> valuesToAdd = new ArrayList<>();
    valuesToAdd.add(increment);
    valuesToAdd.add(member);

    RedisSortedSet sortedSet = new RedisSortedSet(valuesToAdd);
    region.create(key, sortedSet);

    return increment;
  }

  @Override
  List<byte[]> zrange(int min, int max, boolean withScores) {
    return null;
  }

  @Override
  byte[] zscore(byte[] member) {
    return null;
  }

  private Object zaddIncr(Region<RedisKey, RedisData> region, RedisKey key,
      List<byte[]> membersToAdd) {
    // for zadd incr option, only one incrementing element pair is allowed to get here.
    byte[] increment = membersToAdd.get(0);
    byte[] member = membersToAdd.get(1);
    return zincrby(region, key, increment, member);
  }
}
