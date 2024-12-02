package com.ibm.ws.container.service.prototpye.test.pkg;

import java.util.HashMap;
import java.util.Map;

import org.osgi.service.component.annotations.Component;

import com.ibm.ws.container.service.metadata.ApplicationMetaDataListener;
import com.ibm.ws.container.service.metadata.MetaDataEvent;
import com.ibm.ws.container.service.metadata.MetaDataException;
import com.ibm.ws.runtime.metadata.ApplicationMetaData;
import com.ibm.wsspi.annocache.classsource.ClassSource_Factory;
import com.ibm.wsspi.annocache.targets.cache.ApplicationKeyService;

@Component
public class ApplicationKeyServiceImpl implements ApplicationKeyService, ApplicationMetaDataListener {

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

    public static void horribleTestHack(MetaDataEvent<ApplicationMetaData> event) throws MetaDataException {
        String s = event.getMetaData().getJ2EEName().getApplication();
        keys.put(s, new AppKey(s));
    }

    public static void horribleTestHackTwo(MetaDataEvent<ApplicationMetaData> event) throws MetaDataException {
        String s = event.getMetaData().getJ2EEName().getApplication();
        keys.remove(s);
    }

    @Override
    public void applicationMetaDataCreated(MetaDataEvent<ApplicationMetaData> event) throws MetaDataException {
        //We need to ensure that the keys match the inputs to
        //com.ibm.ws.container.service.annocache.internal.AnnotationsImpl.setAppName()

        //Currently I am aware of two paths into that method.
        // CDIArchiveImpl calls it inside getAnnotatedClassesPostBeta()

        //CDIArchiveImpl gets it from ApplicationInfo.getDeploymentName()

        // app manager calls it in EARDeployedAppInfo.hasAnnotationsPostBeta()
        // That class goes through a chain of getters ending in com.ibm.ws.app.manager.internal.ApplicationConfig
        // which reads the server.xml.
        // This should match ApplicationInfo.getDeploymentName()

        String s = event.getMetaData().getJ2EEName().getApplication();
        keys.put(s, new AppKey(s));
    }

    @Override
    public void applicationMetaDataDestroyed(MetaDataEvent<ApplicationMetaData> event) {
        String s = event.getMetaData().getJ2EEName().getApplication();
        keys.remove(s);
    }
}
