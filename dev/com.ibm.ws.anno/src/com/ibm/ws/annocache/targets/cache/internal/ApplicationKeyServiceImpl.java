package com.ibm.ws.annocache.targets.cache.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.osgi.service.component.annotations.Component;

import com.ibm.ws.container.service.app.deploy.ApplicationInfo;
import com.ibm.ws.container.service.state.ApplicationStateListener;
import com.ibm.ws.container.service.state.StateChangeException;
import com.ibm.wsspi.annocache.classsource.ClassSource_Factory;

@Component
public class ApplicationKeyServiceImpl implements ApplicationKeyService, ApplicationStateListener {
    
    private Map<String,AppKey> keys = new HashMap<String,AppKey>();
    
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

    @Override
    public void applicationStarting(ApplicationInfo appInfo) throws StateChangeException {
        //We need to ensure that the keys match the inputs to
        //com.ibm.ws.container.service.annocache.internal.AnnotationsImpl.setAppName()
        
        //Currently I am aware of two paths into that method. 
        // CDIArchiveImpl calls it inside getAnnotatedClassesPostBeta()
        
        //CDIArchiveImpl gets it from ApplicationInfo.getDeploymentName()
        
        // app manager calls it in EARDeployedAppInfo.hasAnnotationsPostBeta()
        // That class goes through a chain of getters ending in com.ibm.ws.app.manager.internal.ApplicationConfig
        // which reads the server.xml.
        // This should match ApplicationInfo.getDeploymentName()        
        
        String s = appInfo.getDeploymentName();
        keys.put(s, new AppKey(s));
    }

    @Override
    public void applicationStarted(ApplicationInfo appInfo) throws StateChangeException {
        //empty by design
    }

    @Override
    public void applicationStopping(ApplicationInfo appInfo) {
        //empty by design
    }

    @Override
    public void applicationStopped(ApplicationInfo appInfo) {
        keys.remove(appInfo.getDeploymentName());
    }
}
