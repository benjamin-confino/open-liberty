package com.ibm.wsspi.annocache.targets.cache;

import java.util.HashMap;
import java.util.Map;

import org.osgi.service.component.annotations.Component;

import com.ibm.wsspi.annocache.classsource.ClassSource_Factory;
import com.ibm.wsspi.annocache.targets.cache.ApplicationKeyService;

@Component
public class ApplicationKeyServiceImpl implements ApplicationKeyService {

    private final static Map<String, AppKey> keys = new HashMap<String, AppKey>();

    public ApplicationKeyServiceImpl() {
        keys.put(ClassSource_Factory.UNNAMED_APP, new AppKey(ClassSource_Factory.UNNAMED_APP));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ApplicationKeyService.AppKey getKeyForApp(String appName) {
        return keys.get(appName);
    }

    public static ApplicationKeyService.AppKey horribleTestHackThree(String appName) {
        return keys.get(appName);
    }

    public static void horribleTestHack(String s) {
        keys.put(s, new AppKey(s));
    }

    public static void horribleTestHackTwo(String s) {
        keys.remove(s);
    }
}
