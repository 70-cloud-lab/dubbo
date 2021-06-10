/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.config.bootstrap;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.config.AbstractInterfaceConfigTest;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.MonitorConfig;
import org.apache.dubbo.config.utils.ConfigValidationUtils;
import org.apache.dubbo.monitor.MonitorService;
import org.apache.dubbo.registry.RegistryService;
import org.apache.dubbo.rpc.model.ApplicationModel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.apache.dubbo.common.constants.CommonConstants.SHUTDOWN_WAIT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SHUTDOWN_WAIT_SECONDS_KEY;

/**
 * {@link DubboBootstrap} Test
 *
 * @since 2.7.5
 */
public class DubboBootstrapTest {

    private static File dubboProperties;

    @BeforeAll
    public static void setUp(@TempDir Path folder) {
        ApplicationModel.reset();
        dubboProperties = folder.resolve(CommonConstants.DUBBO_PROPERTIES_KEY).toFile();
        System.setProperty(CommonConstants.DUBBO_PROPERTIES_KEY, dubboProperties.getAbsolutePath());
    }

    @AfterEach
    public void tearDown() throws IOException {
        ApplicationModel.reset();
    }

    @Test
    public void checkApplication() {
        System.setProperty("dubbo.application.name", "demo");
        ApplicationConfig applicationConfig = new ApplicationConfig();
        applicationConfig.refresh();
        Assertions.assertEquals("demo", applicationConfig.getName());
        System.clearProperty("dubbo.application.name");
    }

    @Test
    public void compatibleApplicationShutdown() {
        try {
            ConfigUtils.setProperties(null);
            System.clearProperty(SHUTDOWN_WAIT_KEY);
            System.clearProperty(SHUTDOWN_WAIT_SECONDS_KEY);

            writeDubboProperties(SHUTDOWN_WAIT_KEY, "100");
            ConfigValidationUtils.validateApplicationConfig(new ApplicationConfig("demo"));
            Assertions.assertEquals("100", System.getProperty(SHUTDOWN_WAIT_KEY));

            System.clearProperty(SHUTDOWN_WAIT_KEY);
            ConfigUtils.setProperties(null);
            writeDubboProperties(SHUTDOWN_WAIT_SECONDS_KEY, "1000");
            ConfigValidationUtils.validateApplicationConfig(new ApplicationConfig("demo"));
            Assertions.assertEquals("1000", System.getProperty(SHUTDOWN_WAIT_SECONDS_KEY));
        } finally {
            ConfigUtils.setProperties(null);
            System.clearProperty("dubbo.application.name");
            System.clearProperty(SHUTDOWN_WAIT_KEY);
            System.clearProperty(SHUTDOWN_WAIT_SECONDS_KEY);
        }
    }

    @Test
    public void testLoadRegistries() {
        try {
            System.setProperty("dubbo.registry.address", "addr1");
            AbstractInterfaceConfigTest.InterfaceConfig interfaceConfig = new AbstractInterfaceConfigTest.InterfaceConfig();
            // FIXME: now we need to check first, then load
            interfaceConfig.setApplication(new ApplicationConfig("testLoadRegistries"));
            interfaceConfig.checkRegistry();
            List<URL> urls = ConfigValidationUtils.loadRegistries(interfaceConfig, true);
            Assertions.assertEquals(1, urls.size());
            URL url = urls.get(0);
            Assertions.assertEquals("registry", url.getProtocol());
            Assertions.assertEquals("addr1:9090", url.getAddress());
            Assertions.assertEquals(RegistryService.class.getName(), url.getPath());
            Assertions.assertTrue(url.getParameters().containsKey("timestamp"));
            Assertions.assertTrue(url.getParameters().containsKey("pid"));
            Assertions.assertTrue(url.getParameters().containsKey("registry"));
            Assertions.assertTrue(url.getParameters().containsKey("dubbo"));
        } finally {
            System.clearProperty("dubbo.registry.address");
        }
    }


    @Test
    public void testLoadMonitor() {
        System.setProperty("dubbo.monitor.address", "monitor-addr:12080");
        System.setProperty("dubbo.monitor.protocol", "monitor");
        AbstractInterfaceConfigTest.InterfaceConfig interfaceConfig = new AbstractInterfaceConfigTest.InterfaceConfig();
        interfaceConfig.setApplication(new ApplicationConfig("testLoadMonitor"));
        interfaceConfig.setMonitor(new MonitorConfig());
        URL url = ConfigValidationUtils.loadMonitor(interfaceConfig, new URL("dubbo", "addr1", 9090));
        Assertions.assertEquals("monitor-addr:12080", url.getAddress());
        Assertions.assertEquals(MonitorService.class.getName(), url.getParameter("interface"));
        Assertions.assertNotNull(url.getParameter("dubbo"));
        Assertions.assertNotNull(url.getParameter("pid"));
        Assertions.assertNotNull(url.getParameter("timestamp"));
    }

    @Test
    public void testLoadUserMonitor() {
        URL registryUrl = URL.valueOf("zookeeper://127.0.0.1:2181");
        // dubbo.monitor.protocol=user
        MonitorConfig monitorConfig1 = new MonitorConfig();
        monitorConfig1.setProtocol("user");
        URL url1 = ConfigValidationUtils.loadMonitor(registryUrl, monitorConfig1, new ApplicationConfig("testLoadMonitor"));
        Assertions.assertEquals("user", url1.getProtocol());

        // dubbo.monitor.protocol=user
        // dubbo.monitor.address=1.2.3.4:5678
        MonitorConfig monitorConfig2 = new MonitorConfig();
        monitorConfig2.setProtocol("user");
        monitorConfig2.setAddress("1.2.3.4:5678");
        URL url2 = ConfigValidationUtils.loadMonitor(registryUrl, monitorConfig2, new ApplicationConfig("testLoadMonitor"));
        Assertions.assertEquals("user", url2.getProtocol());
        Assertions.assertEquals("1.2.3.4:5678", url2.getAddress());

        // dubbo.monitor.address=user://1.2.3.4:5678?k=v
        MonitorConfig monitorConfig3 = new MonitorConfig();
        monitorConfig3.setAddress("user://1.2.3.4:5678?param1=value1");
        URL url3 = ConfigValidationUtils.loadMonitor(registryUrl, monitorConfig3, new ApplicationConfig("testLoadMonitor"));
        Assertions.assertEquals("user", url3.getProtocol());
        Assertions.assertEquals("1.2.3.4:5678", url3.getAddress());
        Assertions.assertEquals("value1", url3.getParameter("param1"));
    }

    private void writeDubboProperties(String key, String value) {
        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(dubboProperties));
            Properties properties = new Properties();
            properties.put(key, value);
            properties.store(os, "");
            os.close();
        } catch (IOException e) {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ioe) {
                    // ignore
                }
            }
        }
    }

}
