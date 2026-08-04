package com.helix.agent.jmx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

public class MBeanRegistry {

    private static final Logger log = LoggerFactory.getLogger(MBeanRegistry.class);
    private static final MBeanServer SERVER = ManagementFactory.getPlatformMBeanServer();

    public static void registerMBean(Object object, String name) {
        try {
            ObjectName objectName = new ObjectName("com.helix.agent:type=" + name);
            if (SERVER.isRegistered(objectName)) {
                SERVER.unregisterMBean(objectName);
            }
            SERVER.registerMBean(object, objectName);
            log.info("Registered MBean: {}", objectName);
        } catch (Exception e) {
            log.error("Failed to register MBean: {}", name, e);
        }
    }

    public static void unregisterMBean(String name) {
        try {
            ObjectName objectName = new ObjectName("com.helix.agent:type=" + name);
            if (SERVER.isRegistered(objectName)) {
                SERVER.unregisterMBean(objectName);
                log.info("Unregistered MBean: {}", objectName);
            }
        } catch (Exception e) {
            log.error("Failed to unregister MBean: {}", name, e);
        }
    }
}
