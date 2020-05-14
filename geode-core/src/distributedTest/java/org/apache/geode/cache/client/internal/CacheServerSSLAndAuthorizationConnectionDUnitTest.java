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
package org.apache.geode.cache.client.internal;

import static org.apache.geode.distributed.ConfigurationProperties.CLUSTER_SSL_CIPHERS;
import static org.apache.geode.distributed.ConfigurationProperties.CLUSTER_SSL_ENABLED;
import static org.apache.geode.distributed.ConfigurationProperties.CLUSTER_SSL_KEYSTORE;
import static org.apache.geode.distributed.ConfigurationProperties.CLUSTER_SSL_KEYSTORE_PASSWORD;
import static org.apache.geode.distributed.ConfigurationProperties.CLUSTER_SSL_KEYSTORE_TYPE;
import static org.apache.geode.distributed.ConfigurationProperties.CLUSTER_SSL_PROTOCOLS;
import static org.apache.geode.distributed.ConfigurationProperties.CLUSTER_SSL_REQUIRE_AUTHENTICATION;
import static org.apache.geode.distributed.ConfigurationProperties.CLUSTER_SSL_TRUSTSTORE;
import static org.apache.geode.distributed.ConfigurationProperties.CLUSTER_SSL_TRUSTSTORE_PASSWORD;
import static org.apache.geode.distributed.ConfigurationProperties.MCAST_PORT;
import static org.apache.geode.distributed.ConfigurationProperties.SECURITY_CLIENT_AUTH_INIT;
import static org.apache.geode.distributed.ConfigurationProperties.SECURITY_COMMON_NAME_AUTH_ENABLED;
import static org.apache.geode.distributed.ConfigurationProperties.SECURITY_MANAGER;
import static org.apache.geode.distributed.ConfigurationProperties.SERVER_SSL_CIPHERS;
import static org.apache.geode.distributed.ConfigurationProperties.SERVER_SSL_ENABLED;
import static org.apache.geode.distributed.ConfigurationProperties.SERVER_SSL_KEYSTORE;
import static org.apache.geode.distributed.ConfigurationProperties.SERVER_SSL_KEYSTORE_PASSWORD;
import static org.apache.geode.distributed.ConfigurationProperties.SERVER_SSL_KEYSTORE_TYPE;
import static org.apache.geode.distributed.ConfigurationProperties.SERVER_SSL_PROTOCOLS;
import static org.apache.geode.distributed.ConfigurationProperties.SERVER_SSL_REQUIRE_AUTHENTICATION;
import static org.apache.geode.distributed.ConfigurationProperties.SERVER_SSL_TRUSTSTORE;
import static org.apache.geode.distributed.ConfigurationProperties.SERVER_SSL_TRUSTSTORE_PASSWORD;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_CIPHERS;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_ENABLED_COMPONENTS;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_KEYSTORE;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_KEYSTORE_PASSWORD;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_KEYSTORE_TYPE;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_PROTOCOLS;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_REQUIRE_AUTHENTICATION;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_TRUSTSTORE;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_TRUSTSTORE_PASSWORD;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_WEB_SERVICE_REQUIRE_AUTHENTICATION;
import static org.apache.geode.test.dunit.IgnoredException.addIgnoredException;
import static org.apache.geode.test.dunit.Invoke.invokeInEveryVM;
import static org.apache.geode.test.dunit.VM.getHostName;
import static org.apache.geode.test.dunit.VM.getVM;
import static org.apache.geode.test.util.ResourceUtils.createTempFileFromResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionFactory;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.client.NoAvailableServersException;
import org.apache.geode.cache.server.CacheServer;
import org.apache.geode.distributed.Locator;
import org.apache.geode.examples.SimpleSecurityManager;
import org.apache.geode.examples.SimpleSecurityManagerAllowsCNAuthorization;
import org.apache.geode.internal.security.SecurableCommunicationChannel;
import org.apache.geode.security.AuthenticationRequiredException;
import org.apache.geode.security.templates.UserPasswordAuthInit;
import org.apache.geode.test.dunit.AsyncInvocation;
import org.apache.geode.test.dunit.IgnoredException;
import org.apache.geode.test.dunit.RMIException;
import org.apache.geode.test.dunit.VM;
import org.apache.geode.test.dunit.internal.JUnit4DistributedTestCase;
import org.apache.geode.test.dunit.rules.DistributedRestoreSystemProperties;
import org.apache.geode.test.junit.categories.ClientServerTest;
import org.apache.geode.test.junit.runners.CategoryWithParameterizedRunnerFactory;

/**
 * Tests ssl with authorization
 */
@Category({ClientServerTest.class})
@RunWith(Parameterized.class)
@UseParametersRunnerFactory(CategoryWithParameterizedRunnerFactory.class)
@SuppressWarnings("serial")
public class CacheServerSSLAndAuthorizationConnectionDUnitTest extends JUnit4DistributedTestCase {

  private static final String CLIENT_KEY_STORE = "default.keystore";
  private static final String CLIENT_TRUST_STORE = "default.keystore";
  private static final String SERVER_KEY_STORE = "default.keystore";
  private static final String SERVER_TRUST_STORE = "default.keystore";

  private static CacheServerSSLAndAuthorizationConnectionDUnitTest instance;

  private Cache cache;
  private CacheServer cacheServer;
  private ClientCache clientCache;
  private int cacheServerPort;
  private String hostName;

  private static boolean useOldSSLSettings;

  @Parameters
  public static Collection<Boolean> data() {
    List<Boolean> result = new ArrayList<>();
    result.add(Boolean.TRUE);
    result.add(Boolean.FALSE);
    return result;
  }

  public CacheServerSSLAndAuthorizationConnectionDUnitTest(Boolean useOldSSLSettings) {
    CacheServerSSLAndAuthorizationConnectionDUnitTest.useOldSSLSettings = useOldSSLSettings;
  }

  @Rule
  public DistributedRestoreSystemProperties restoreSystemProperties =
      new DistributedRestoreSystemProperties();

  @AfterClass
  public static void postClass() {
    invokeInEveryVM(() -> {
      if (instance.cache != null) {
        instance.cache.close();
      }
      instance = null;
    });
    if (instance.cache != null) {
      instance.cache.close();
    }
    instance = null;
  }

  @Before
  public void setUp() {
    disconnectAllFromDS();
    instance = this;
    invokeInEveryVM(() -> instance = new CacheServerSSLAndAuthorizationConnectionDUnitTest(useOldSSLSettings));
  }

  @After
  public void tearDown() {
    VM serverVM = getVM(1);
    VM clientVM = getVM(2);

    clientVM.invoke(() -> closeClientCacheTask());
    serverVM.invoke(() -> closeCacheTask());
  }

  private Cache createCache(Properties props) throws Exception {
    props.setProperty(MCAST_PORT, "0");
    cache = new CacheFactory(props).create();
    if (cache == null) {
      throw new Exception("CacheFactory.create() returned null ");
    }
    return cache;
  }

  private int createServer() throws IOException {
    cacheServer = cache.addCacheServer();
    cacheServer.setPort(0);
    cacheServer.start();
    hostName = cacheServer.getHostnameForClients();
    cacheServerPort = cacheServer.getPort();
    return cacheServerPort;
  }

  private int getCacheServerPort() {
    return cacheServerPort;
  }

  private String getCacheServerHost() {
    return hostName;
  }

  private void setUpServerVM(final boolean cacheServerSslenabled, int optionalLocatorPort)
      throws Exception {
    System.setProperty("javax.net.debug", "ssl,handshake");

    Properties gemFireProps = new Properties();
    gemFireProps.setProperty("log-level", "debug");
    if (optionalLocatorPort > 0) {
      gemFireProps.setProperty("locators", "localhost[" + optionalLocatorPort + "]");
    }

    String cacheServerSslprotocols = "any";
    String cacheServerSslciphers = "any";
    boolean cacheServerSslRequireAuth = true;
    if (!useOldSSLSettings) {
      getNewSSLSettings(gemFireProps, cacheServerSslprotocols, cacheServerSslciphers,
          cacheServerSslRequireAuth);
    } else {
      gemFireProps.setProperty(CLUSTER_SSL_ENABLED, String.valueOf(cacheServerSslenabled));
      gemFireProps.setProperty(CLUSTER_SSL_PROTOCOLS, cacheServerSslprotocols);
      gemFireProps.setProperty(CLUSTER_SSL_CIPHERS, cacheServerSslciphers);
      gemFireProps.setProperty(CLUSTER_SSL_REQUIRE_AUTHENTICATION,
          String.valueOf(cacheServerSslRequireAuth));

      String keyStore =
          createTempFileFromResource(CacheServerSSLAndAuthorizationConnectionDUnitTest.class, SERVER_KEY_STORE)
              .getAbsolutePath();
      String trustStore =
          createTempFileFromResource(CacheServerSSLAndAuthorizationConnectionDUnitTest.class,
              SERVER_TRUST_STORE).getAbsolutePath();
      gemFireProps.setProperty(CLUSTER_SSL_KEYSTORE_TYPE, "jks");
      gemFireProps.setProperty(CLUSTER_SSL_KEYSTORE, keyStore);
      gemFireProps.setProperty(CLUSTER_SSL_KEYSTORE_PASSWORD, "password");
      gemFireProps.setProperty(CLUSTER_SSL_TRUSTSTORE, trustStore);
      gemFireProps.setProperty(CLUSTER_SSL_TRUSTSTORE_PASSWORD, "password");
    }
    gemFireProps.setProperty(SECURITY_COMMON_NAME_AUTH_ENABLED, "true");
    gemFireProps.setProperty(SSL_WEB_SERVICE_REQUIRE_AUTHENTICATION, "true");
    gemFireProps.setProperty("security-username", "cluster");
    gemFireProps.setProperty("security-password", "cluster");
    StringWriter sw = new StringWriter();
    PrintWriter writer = new PrintWriter(sw);
    gemFireProps.list(writer);
    System.out.println("Starting cacheserver ds with following properties \n" + sw);
    createCache(gemFireProps);

    RegionFactory factory = cache.createRegionFactory(RegionShortcut.REPLICATE);
    Region r = factory.create("serverRegion");
    r.put("serverkey", "servervalue");
  }

  private void getNewSSLSettings(Properties gemFireProps, String cacheServerSslprotocols,
      String cacheServerSslciphers, boolean cacheServerSslRequireAuth) {
    gemFireProps.setProperty(SSL_ENABLED_COMPONENTS,
        SecurableCommunicationChannel.ALL.toString());
    gemFireProps.setProperty(SSL_PROTOCOLS, cacheServerSslprotocols);
    gemFireProps.setProperty(SSL_CIPHERS, cacheServerSslciphers);
    gemFireProps.setProperty(SSL_REQUIRE_AUTHENTICATION, String.valueOf(cacheServerSslRequireAuth));

    String keyStore =
        createTempFileFromResource(CacheServerSSLAndAuthorizationConnectionDUnitTest.class, SERVER_KEY_STORE)
            .getAbsolutePath();
    String trustStore =
        createTempFileFromResource(CacheServerSSLAndAuthorizationConnectionDUnitTest.class, SERVER_TRUST_STORE)
            .getAbsolutePath();
    gemFireProps.setProperty(SSL_KEYSTORE_TYPE, "jks");
    gemFireProps.setProperty(SSL_KEYSTORE, keyStore);
    gemFireProps.setProperty(SSL_KEYSTORE_PASSWORD, "password");
    gemFireProps.setProperty(SSL_TRUSTSTORE, trustStore);
    gemFireProps.setProperty(SSL_TRUSTSTORE_PASSWORD, "password");
  }

  private void setUpClientVM(String host, int port, boolean cacheServerSslenabled,
      boolean cacheServerSslRequireAuth, String keyStore, String trustStore, boolean subscription,
      boolean clientHasTrustedKeystore) {
    System.setProperty("javax.net.debug", "ssl,handshake");
    Properties gemFireProps = new Properties();

    String cacheServerSslprotocols = "any";
    String cacheServerSslciphers = "any";

    String keyStorePath =
        createTempFileFromResource(CacheServerSSLAndAuthorizationConnectionDUnitTest.class, keyStore)
            .getAbsolutePath();
    String trustStorePath =
        createTempFileFromResource(CacheServerSSLAndAuthorizationConnectionDUnitTest.class, trustStore)
            .getAbsolutePath();

    if (cacheServerSslenabled) {
      if (useOldSSLSettings) {
        gemFireProps.setProperty(SERVER_SSL_ENABLED, String.valueOf(cacheServerSslenabled));
        gemFireProps.setProperty(SERVER_SSL_PROTOCOLS, cacheServerSslprotocols);
        gemFireProps.setProperty(SERVER_SSL_CIPHERS, cacheServerSslciphers);
        gemFireProps.setProperty(SERVER_SSL_REQUIRE_AUTHENTICATION,
            String.valueOf(cacheServerSslRequireAuth));
        if (clientHasTrustedKeystore) {
          gemFireProps.setProperty(SERVER_SSL_KEYSTORE_TYPE, "jks");
          gemFireProps.setProperty(SERVER_SSL_KEYSTORE, keyStorePath);
          gemFireProps.setProperty(SERVER_SSL_KEYSTORE_PASSWORD, "password");
          gemFireProps.setProperty(SERVER_SSL_TRUSTSTORE, trustStorePath);
          gemFireProps.setProperty(SERVER_SSL_TRUSTSTORE_PASSWORD, "password");
        } else {
          gemFireProps.setProperty(SERVER_SSL_KEYSTORE_TYPE, "jks");
          gemFireProps.setProperty(SERVER_SSL_KEYSTORE, "");
          gemFireProps.setProperty(SERVER_SSL_KEYSTORE_PASSWORD, "password");
          gemFireProps.setProperty(SERVER_SSL_TRUSTSTORE, trustStorePath);
          gemFireProps.setProperty(SERVER_SSL_TRUSTSTORE_PASSWORD, "password");
        }
      } else {
        gemFireProps.setProperty(SSL_ENABLED_COMPONENTS, "all");
        gemFireProps.setProperty(SSL_CIPHERS, cacheServerSslciphers);
        gemFireProps.setProperty(SSL_PROTOCOLS, cacheServerSslprotocols);
        gemFireProps
            .setProperty(SSL_REQUIRE_AUTHENTICATION, String.valueOf(cacheServerSslRequireAuth));
        if (clientHasTrustedKeystore) {
          gemFireProps.setProperty(SSL_KEYSTORE_TYPE, "jks");
          gemFireProps.setProperty(SSL_KEYSTORE, keyStorePath);
          gemFireProps.setProperty(SSL_KEYSTORE_PASSWORD, "password");
          gemFireProps.setProperty(SSL_TRUSTSTORE, trustStorePath);
          gemFireProps.setProperty(SSL_TRUSTSTORE_PASSWORD, "password");

        } else {
          gemFireProps.setProperty(SSL_KEYSTORE_TYPE, "jks");
          gemFireProps.setProperty(SSL_KEYSTORE, "");
          gemFireProps.setProperty(SSL_KEYSTORE_PASSWORD, "password");
          gemFireProps.setProperty(SSL_TRUSTSTORE, trustStorePath);
          gemFireProps.setProperty(SSL_TRUSTSTORE_PASSWORD, "password");
        }
      }
    }
//    gemFireProps.setProperty(UserPasswordAuthInit.USER_NAME, "data");
//    gemFireProps.setProperty(UserPasswordAuthInit.PASSWORD, "data");
//    gemFireProps.setProperty(SECURITY_CLIENT_AUTH_INIT, UserPasswordAuthInit.class.getName());
    StringWriter sw = new StringWriter();
    PrintWriter writer = new PrintWriter(sw);
    gemFireProps.list(writer);
    System.out.println("Starting client ds with following properties \n" + sw.getBuffer());

    ClientCacheFactory clientCacheFactory = new ClientCacheFactory(gemFireProps);
    clientCacheFactory.setPoolSubscriptionEnabled(subscription).addPoolServer(host, port);
    clientCacheFactory.setPoolRetryAttempts(5);
    clientCache = clientCacheFactory.create();

    ClientRegionFactory<String, String> regionFactory =
        clientCache.createClientRegionFactory(ClientRegionShortcut.PROXY);
    Region<String, String> region = regionFactory.create("serverRegion");
    assertNotNull(region);
  }

  private void doClientRegionTest() {
    Region<String, String> region = clientCache.getRegion("serverRegion");
    assertEquals("servervalue", region.get("serverkey"));
    region.put("clientkey", "clientvalue");
    assertEquals("clientvalue", region.get("clientkey"));
  }

  private void doServerRegionTest() {
    Region<String, String> region = cache.getRegion("serverRegion");
    assertEquals("servervalue", region.get("serverkey"));
    assertEquals("clientvalue", region.get("clientkey"));
  }


  private static void setUpServerVMTask(boolean cacheServerSslenabled, int optionalLocatorPort)
      throws Exception {
    instance.setUpServerVM(cacheServerSslenabled, optionalLocatorPort);
  }

  private static int createServerTask() throws Exception {
    return instance.createServer();
  }

  private static void setUpClientVMTask(String host, int port, boolean cacheServerSslenabled,
      boolean cacheServerSslRequireAuth, String keyStore, String trustStore,
      boolean clientHasTrustedKeystore) {
    instance.setUpClientVM(host, port, cacheServerSslenabled, cacheServerSslRequireAuth, keyStore,
        trustStore, true, clientHasTrustedKeystore);
  }

  private static void setUpClientVMTaskNoSubscription(String host, int port,
      boolean cacheServerSslenabled, boolean cacheServerSslRequireAuth, String keyStore,
      String trustStore) {
    instance.setUpClientVM(host, port, cacheServerSslenabled, cacheServerSslRequireAuth, keyStore,
        trustStore, false, true);
  }

  private static void doClientRegionTestTask() {
    instance.doClientRegionTest();
  }

  private static void verifyServerDoesNotReceiveClientUpdate() {
    instance.doVerifyServerDoesNotReceiveClientUpdate();
  }

  private void doVerifyServerDoesNotReceiveClientUpdate() {
    Region<String, String> region = cache.getRegion("serverRegion");
    assertFalse(region.containsKey("clientkey"));
  }

  private static void doServerRegionTestTask() {
    instance.doServerRegionTest();
  }

  private static Object[] getCacheServerEndPointTask() { // TODO: avoid Object[]
    Object[] array = new Object[2];
    array[0] = instance.getCacheServerHost();
    array[1] = instance.getCacheServerPort();
    return array;
  }

  private static void closeCacheTask() {
    if (instance != null && instance.cache != null) {
      instance.cache.close();
    }
  }

  private static void closeClientCacheTask() {
    if (instance != null && instance.clientCache != null) {
      instance.clientCache.close();
    }
  }

  @Test
  public void testCacheServerSSLWithAuthoirization() throws Exception {
    VM serverVM = getVM(1);
    VM clientVM = getVM(2);
    VM serverVM2 = getVM(3);

    boolean cacheServerSslenabled = true;
    boolean cacheClientSslenabled = true;
    boolean cacheClientSslRequireAuth = true;

    Properties locatorProps = new Properties();
    String cacheServerSslprotocols = "any";
    String cacheServerSslciphers = "any";
    boolean cacheServerSslRequireAuth = true;
    getNewSSLSettings(locatorProps, cacheServerSslprotocols, cacheServerSslciphers,
        cacheServerSslRequireAuth);
    locatorProps.setProperty(SECURITY_MANAGER, SimpleSecurityManagerAllowsCNAuthorization.class.getCanonicalName());
    Locator locator = Locator.startLocatorAndDS(0, new File(""), locatorProps);
    int locatorPort = locator.getPort();
    try {
      serverVM.invoke(() -> setUpServerVMTask(cacheServerSslenabled, locatorPort));
      int port = serverVM.invoke(() -> createServerTask());
      serverVM2.invoke(() -> setUpServerVMTask(cacheServerSslenabled, locatorPort));
      serverVM2.invoke(() -> createServerTask());

      String hostName = getHostName();

      clientVM.invoke(() -> setUpClientVMTask(hostName, port, cacheClientSslenabled,
          cacheClientSslRequireAuth, CLIENT_KEY_STORE, CLIENT_TRUST_STORE, true));
      clientVM.invoke(() -> doClientRegionTestTask());
      serverVM.invoke(() -> doServerRegionTestTask());
    } finally {
      locator.stop();
    }
  }
}
