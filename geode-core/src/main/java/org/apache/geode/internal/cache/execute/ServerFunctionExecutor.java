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

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.client.ServerConnectivityException;
import org.apache.geode.cache.client.internal.ExecuteFunctionNoAckOp;
import org.apache.geode.cache.client.internal.ExecuteFunctionOp;
import org.apache.geode.cache.client.internal.ExecuteFunctionOp.ExecuteFunctionOpImpl;
import org.apache.geode.cache.client.internal.GetFunctionAttributeOp;
import org.apache.geode.cache.client.internal.PoolImpl;
import org.apache.geode.cache.client.internal.ProxyCache;
import org.apache.geode.cache.client.internal.UserAttributes;
import org.apache.geode.cache.execute.Execution;
import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionException;
import org.apache.geode.cache.execute.FunctionService;
import org.apache.geode.cache.execute.ResultCollector;
import org.apache.geode.internal.cache.TXManagerImpl;
import org.apache.geode.internal.cache.execute.util.SynchronizedResultCollector;
import org.apache.geode.internal.logging.LogService;

public class ServerFunctionExecutor extends AbstractExecution {
  private static final Logger logger = LogService.getLogger();

  private PoolImpl pool;

  private final boolean allServers;

  private String[] groups;

  private ExecutorService executorService;

  ServerFunctionExecutor(Pool pool, boolean allServers, ExecutorService executorService,
      String... groups) {
    this.pool = (PoolImpl) pool;
    this.allServers = allServers;
    this.groups = groups;
    this.executorService = executorService;
  }

  ServerFunctionExecutor(Pool pool, boolean allServers, ProxyCache proxyCache,
      ExecutorService executorService, String... groups) {
    this.pool = (PoolImpl) pool;
    this.allServers = allServers;
    this.proxyCache = proxyCache;
    this.groups = groups;
    this.executorService = executorService;
  }

  private ServerFunctionExecutor(ServerFunctionExecutor sfe) {
    super(sfe);
    if (sfe.pool != null) {
      pool = sfe.pool;
    }
    allServers = sfe.allServers;
    groups = sfe.groups;
    executorService = sfe.executorService;
  }

  private ServerFunctionExecutor(ServerFunctionExecutor sfe, Object args) {
    this(sfe);
    this.args = args;
    executorService = sfe.executorService;
  }

  private ServerFunctionExecutor(ServerFunctionExecutor sfe, ResultCollector collector) {
    this(sfe);
    rc = collector != null ? new SynchronizedResultCollector(collector) : null;
    executorService = sfe.executorService;
  }

  private ServerFunctionExecutor(ServerFunctionExecutor sfe, MemberMappedArgument argument) {
    this(sfe);
    memberMappedArg = argument;
    isMemberMappedArgument = true;
    executorService = sfe.executorService;
  }

  protected ResultCollector executeFunction(final String functionId, boolean result, boolean isHA,
      boolean optimizeForWrite, long timeout, TimeUnit unit) {
    try {
      if (proxyCache != null) {
        if (proxyCache.isClosed()) {
          throw proxyCache.getCacheClosedException("Cache is closed for this user.");
        }
        UserAttributes.userAttributes.set(proxyCache.getUserAttributes());
      }

      byte hasResult = 0;
      if (result) {
        hasResult = 1;
        if (rc == null) {
          ResultCollector defaultCollector = new DefaultResultCollector();
          return executeOnServer(functionId, defaultCollector, hasResult, isHA, optimizeForWrite,
              timeout, unit);
        } else {
          return executeOnServer(functionId, rc, hasResult, isHA, optimizeForWrite, timeout, unit);
        }
      } else {
        executeOnServerNoAck(functionId, hasResult, isHA, optimizeForWrite);
        return new NoResult();
      }
    } finally {
      UserAttributes.userAttributes.set(null);
    }
  }

  @Override
  protected ResultCollector executeFunction(final Function function, long timeout, TimeUnit unit) {
    byte hasResult = 0;
    try {
      if (proxyCache != null) {
        if (proxyCache.isClosed()) {
          throw proxyCache.getCacheClosedException("Cache is closed for this user.");
        }
        UserAttributes.userAttributes.set(proxyCache.getUserAttributes());
      }

      if (function.hasResult()) {
        hasResult = 1;

        if (rc == null) {
          ResultCollector defaultCollector = new DefaultResultCollector();
          return executeOnServer(function, defaultCollector, hasResult, timeout, unit);
        } else {
          return executeOnServer(function, rc, hasResult, timeout, unit);
        }
      } else {
        executeOnServerNoAck(function, hasResult);
        return new NoResult();
      }
    } finally {
      UserAttributes.userAttributes.set(null);
    }
  }

  private ResultCollector executeOnServer(Function function, String functionId, ResultCollector rc,
      byte hasResult,
      boolean isHA, boolean optimizeForWrite, long timeout, TimeUnit unit) {
    final String localFunctionId = (function != null) ? function.getId() : functionId;
    FunctionStats stats = FunctionStats.getFunctionStats(localFunctionId);
    int socketReadTimeout = getTimeoutMs();
    try {
      validateExecution(null, null);
      long start = stats.startTime();
      stats.startFunctionExecution(true);
      final ExecuteFunctionOpImpl executeFunctionOp;
      final Supplier<ExecuteFunctionOpImpl> executeFunctionOpSupplier;
      final Supplier<ExecuteFunctionOpImpl> reExecuteFunctionOpSupplier;
      if (function != null) {
        executeFunctionOp =
            new ExecuteFunctionOpImpl(function, args, memberMappedArg,
                rc, isFnSerializationReqd, (byte) 0, groups, allServers, isIgnoreDepartedMembers(),
                socketReadTimeout);

        executeFunctionOpSupplier =
            () -> new ExecuteFunctionOpImpl(function, args, memberMappedArg,
                rc, isFnSerializationReqd, (byte) 0,
                null/* onGroups does not use single-hop for now */,
                false, false, socketReadTimeout);

        reExecuteFunctionOpSupplier =
            () -> new ExecuteFunctionOpImpl(function, this.getArguments(),
                this.getMemberMappedArgument(), rc,
                isFnSerializationReqd, (byte) 1, groups, allServers,
                this.isIgnoreDepartedMembers(), socketReadTimeout);
      } else {
        executeFunctionOp =
            new ExecuteFunctionOpImpl(functionId, args, memberMappedArg, hasResult,
                rc, isFnSerializationReqd, isHA, optimizeForWrite, (byte) 0, groups, allServers,
                this.isIgnoreDepartedMembers(), socketReadTimeout);

        executeFunctionOpSupplier =
            () -> new ExecuteFunctionOpImpl(functionId, args, memberMappedArg,
                hasResult,
                rc, isFnSerializationReqd, isHA, optimizeForWrite, (byte) 0,
                null/* onGroups does not use single-hop for now */, false, false,
                socketReadTimeout);

        reExecuteFunctionOpSupplier =
            () -> new ExecuteFunctionOpImpl(functionId, args,
                this.getMemberMappedArgument(),
                hasResult, rc, isFnSerializationReqd, isHA, optimizeForWrite, (byte) 1,
                groups, allServers, this.isIgnoreDepartedMembers(), socketReadTimeout);
      }
      // TODO alberto.gomez: Do we really want to have the following if-else?
      // If timeout > 0 then the external behavior would be blocking as it can be seen below
      // although the code would be more efficient (less threads) with this "if-else".
      if (getIsAsyncClientFunctionExecution()) {
        ProxyResultCollector proxyCollector = new ProxyResultCollector();
        final Callable callableObj;
        callableObj = () -> {
          ExecuteFunctionOp.execute(pool, allServers,
              rc, isHA,
              UserAttributes.userAttributes.get(), groups,
              executeFunctionOp,
              executeFunctionOpSupplier,
              reExecuteFunctionOpSupplier);
          stats.endFunctionExecution(start, true);
          return rc;
        };
        Future<ResultCollector> future =
            (Future<ResultCollector>) executorService.submit(callableObj);
        proxyCollector.setFuture(future);
        if (timeout > 0) {
          proxyCollector.getResult(timeout, unit);
        }
        return proxyCollector;
      } else {
        ExecuteFunctionOp.execute(pool, allServers,
            rc, isHA,
            UserAttributes.userAttributes.get(), groups,
            executeFunctionOp,
            executeFunctionOpSupplier,
            reExecuteFunctionOpSupplier);
        stats.endFunctionExecution(start, true);
        return rc;
      }
    } catch (FunctionException functionException) {
      stats.endFunctionExecutionWithException(true);
      throw functionException;
    } catch (ServerConnectivityException exception) {
      throw exception;
    } catch (Exception exception) {
      stats.endFunctionExecutionWithException(true);
      throw new FunctionException(exception);
    }
  }

  private ResultCollector executeOnServer(Function function, ResultCollector rc, byte hasResult,
      long timeout, TimeUnit unit) {
    return executeOnServer(function, null, rc, hasResult, false, false, timeout, unit);
  }

  private ResultCollector executeOnServer(String functionId, ResultCollector rc, byte hasResult,
      boolean isHA, boolean optimizeForWrite, long timeout, TimeUnit unit) {
    return executeOnServer(null, functionId, rc, hasResult, isHA, optimizeForWrite, timeout, unit);
  }

  private void executeOnServerNoAck(Function function, byte hasResult) {
    FunctionStats stats = FunctionStats.getFunctionStats(function.getId());
    try {
      validateExecution(function, null);
      long start = stats.startTime();
      stats.startFunctionExecution(false);
      ExecuteFunctionNoAckOp.execute(pool, function, args, memberMappedArg, allServers,
          hasResult, isFnSerializationReqd, groups);
      stats.endFunctionExecution(start, false);
    } catch (FunctionException functionException) {
      stats.endFunctionExecutionWithException(false);
      throw functionException;
    } catch (ServerConnectivityException exception) {
      throw exception;
    } catch (Exception exception) {
      stats.endFunctionExecutionWithException(false);
      throw new FunctionException(exception);
    }
  }

  private void executeOnServerNoAck(String functionId, byte hasResult, boolean isHA,
      boolean optimizeForWrite) {
    FunctionStats stats = FunctionStats.getFunctionStats(functionId);
    try {
      validateExecution(null, null);
      long start = stats.startTime();
      stats.startFunctionExecution(false);
      ExecuteFunctionNoAckOp.execute(pool, functionId, args, memberMappedArg, allServers,
          hasResult, isFnSerializationReqd, isHA, optimizeForWrite, groups);
      stats.endFunctionExecution(start, false);
    } catch (FunctionException functionException) {
      stats.endFunctionExecutionWithException(false);
      throw functionException;
    } catch (ServerConnectivityException exception) {
      throw exception;
    } catch (Exception exception) {
      stats.endFunctionExecutionWithException(false);
      throw new FunctionException(exception);
    }
  }

  public Pool getPool() {
    return pool;
  }

  @Override
  public Execution withFilter(Set filter) {
    throw new FunctionException(
        String.format("Cannot specify %s for data independent functions",
            "filter"));
  }

  @Override
  public InternalExecution withBucketFilter(Set<Integer> bucketIDs) {
    throw new FunctionException(
        String.format("Cannot specify %s for data independent functions",
            "buckets as filter"));
  }

  @Override
  public Execution setArguments(Object args) {
    if (args == null) {
      throw new FunctionException(
          String.format("The input %s for the execute function request is null",
              "args"));
    }
    return new ServerFunctionExecutor(this, args);
  }

  @Override
  public Execution withArgs(Object args) {
    return setArguments(args);
  }

  @Override
  public Execution withCollector(ResultCollector rs) {
    if (rs == null) {
      throw new FunctionException(
          String.format("The input %s for the execute function request is null",
              "Result Collector"));
    }
    return new ServerFunctionExecutor(this, rs);
  }

  @Override
  public InternalExecution withMemberMappedArgument(MemberMappedArgument argument) {
    if (argument == null) {
      throw new FunctionException(
          String.format("The input %s for the execute function request is null",
              "MemberMapped Args"));
    }
    return new ServerFunctionExecutor(this, argument);
  }

  @Override
  public void validateExecution(Function function, Set targetMembers) {
    if (TXManagerImpl.getCurrentTXUniqueId() != TXManagerImpl.NOTX) {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public ResultCollector execute(final String functionName, long timeout, TimeUnit unit) {
    if (functionName == null) {
      throw new FunctionException(
          "The input function for the execute function request is null");
    }
    isFnSerializationReqd = false;
    Function functionObject = FunctionService.getFunction(functionName);
    if (functionObject == null) {
      byte[] functionAttributes = getFunctionAttributes(functionName);
      if (functionAttributes == null) {
        // Set authentication properties before executing the internal function.
        try {
          if (proxyCache != null) {
            if (proxyCache.isClosed()) {
              throw proxyCache.getCacheClosedException("Cache is closed for this user.");
            }
            UserAttributes.userAttributes.set(proxyCache.getUserAttributes());
          }

          Object obj = GetFunctionAttributeOp.execute(pool, functionName);
          functionAttributes = (byte[]) obj;
          addFunctionAttributes(functionName, functionAttributes);
        } finally {
          UserAttributes.userAttributes.set(null);
        }
      }

      boolean isHA = functionAttributes[1] == 1;
      boolean hasResult = functionAttributes[0] == 1;
      boolean optimizeForWrite = functionAttributes[2] == 1;
      return executeFunction(functionName, hasResult, isHA, optimizeForWrite, timeout, unit);
    } else {
      return executeFunction(functionObject, timeout, unit);
    }

  }

  @Override
  public ResultCollector execute(final String functionName) {
    return execute(functionName, 0, null);
  }
}
