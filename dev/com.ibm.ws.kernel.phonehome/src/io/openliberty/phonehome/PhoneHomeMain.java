package io.openliberty.phonehome;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ibm.websphere.kernel.server.ServerInfoMBean;
import com.ibm.ws.kernel.feature.FeatureProvisioner;

import io.openliberty.checkpoint.spi.CheckpointHook;
import io.openliberty.checkpoint.spi.CheckpointPhase;

public class PhoneHomeMain implements Runnable {
	
	private final List<PhoneHomeDataSource> dataSources;
	private final FeatureProvisioner featureProvisioner;
	private final ServerInfoMBean serverInfo;
	private final Map<String, Object> properties;
	
	public PhoneHomeMain(Map<String, Object> properties, List<PhoneHomeDataSource> dataSources,
			FeatureProvisioner featureProvisioner, ServerInfoMBean serverInfo) {
		this.dataSources = dataSources;
		this.featureProvisioner = featureProvisioner;
		this.serverInfo = serverInfo;
		this.properties = properties;
	}

	@Override
	public void run() {
		try {
	            System.out.println("GREPactivate realupdated");

	            //First we check server.xml, bootstrap.properties, and server.env to see if any are explicitly disabling phone home.
	            //The proper way to disable phone home is to set enabled to "false", but check for possible mistakes
	            //because this is a user's data.

	            Set<String> disablingWords = new HashSet<String>(Arrays.asList("disabled", "false", "flse", "fase", "fale", "fals", "no", "0"));
	            Set<String> discoveredProperties = new HashSet<String>();

	            //server.xml
	            if (properties.containsKey("enabled") && 
	                    discoveredProperties.add((String) properties.get("enabled")));

	            //server.env
	            if (System.getenv("phoneHomeEnabled") != null && 
	                    discoveredProperties.add(System.getenv("phoneHomeEnabled")));

	            //bootstrap.properties
	            if (System.getProperty("phoneHomeEnabled") != null && 
	                    discoveredProperties.add(System.getProperty("phoneHomeEnabled")));    

	            disablingWords.retainAll(discoveredProperties);
	            if (disablingWords.isEmpty() && discoveredProperties.contains("true")) {
	            	System.out.println("GREPactivate was true");

	                //Run phoneHome immediately
	            	PhoneHomeDataCollector collector = new PhoneHomeDataCollector(dataSources, featureProvisioner, serverInfo);
	            	PhoneHomePhoneCaller.sendData(collector.getData());

	                //Setup phone home to run on a checkpoint restore
	                CheckpointHook checkpointHook = new CheckpointHook() {

	                    @Override
	                    public void restore() {
	                    	try {
	                    	    PhoneHomeDataCollector collector = new PhoneHomeDataCollector(dataSources, featureProvisioner, serverInfo);
	                    	    PhoneHomePhoneCaller.sendData(collector.getData());
	                    	} catch (Throwable ignored ) {}
	                    }
	                };
	                CheckpointPhase.getPhase().addMultiThreadedHook(checkpointHook);

	            }
	        

		} catch (Throwable ignored) {}
		
	}

}
