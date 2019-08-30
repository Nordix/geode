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
package org.apache.geode.internal.cache.execute;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.Logger;

import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionException;
import org.apache.geode.cache.execute.ResultCollector;
import org.apache.geode.distributed.DistributedMember;
import org.apache.geode.internal.logging.LogService;

/**
 * Encapsulates a result collector inside a Future object. Only to be used by clients to retrieve
 * data.
 *
 * @since GemFire 6.0
 *
 */
public class ProxyResultCollector implements ResultCollector {
  private static final Logger logger = LogService.getLogger();


  private Future<ResultCollector> future;

  public ProxyResultCollector() {}

  /**
   * Adds a single function execution result from a remote node to the ResultCollector
   *
   */
  @Override
  public synchronized void addResult(DistributedMember distributedMember,
      Object resultOfSingleExecution) {}

  /**
   * Waits if necessary for the computation to complete, and then retrieves its result.<br>
   * If {@link Function#hasResult()} is false, upon calling {@link ResultCollector#getResult()}
   * throws {@link FunctionException}.
   *
   * @return the Object computed result
   * @throws FunctionException if something goes wrong while retrieving the result
   */
  @Override
  public Object getResult() throws FunctionException {
    Object result = null;
    try {
      result = future.get().getResult();
    } catch (InterruptedException e) {
      throw new FunctionException("Interrupted exception: " + e.getMessage());
    } catch (ExecutionException e) {
      throw new FunctionException("Execution exception: " + e.getMessage());
    }
    return result;
  }

  /**
   * Call back provided to caller, which is called after function execution is complete and caller
   * can retrieve results using {@link ResultCollector#getResult()}
   *
   */
  @Override
  public void endResults() {}

  /**
   * Waits if necessary for at most the given time for the computation to complete, and then
   * retrieves its result, if available. <br>
   * If {@link Function#hasResult()} is false, upon calling {@link ResultCollector#getResult()}
   * throws {@link FunctionException}.
   *
   * @param timeout the maximum time to wait
   * @param unit the time unit of the timeout argument
   * @return Object computed result
   * @throws FunctionException if something goes wrong while retrieving the result
   */
  @Override
  public Object getResult(long timeout, TimeUnit unit)
      throws FunctionException, InterruptedException {
    Object result = null;
    ResultCollector rc = null;
    try {
      rc = future.get(timeout, unit);
    } catch (ExecutionException e) {
      throw new FunctionException("Execution exception: " + e.getMessage());
    } catch (TimeoutException e) {
      throw new FunctionException("Timeout exception: " + e.getMessage());
    } finally {
      if (!future.isCancelled()) {
        future.cancel(true);
      }
    }
    return rc.getResult();
  }

  /**
   * Does nothing
   *
   */
  @Override
  public void clearResults() {}

  public void setFuture(Future<ResultCollector> future) {
    this.future = future;
  }
}
