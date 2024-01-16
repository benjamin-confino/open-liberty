package io.openliberty.phonehome;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.ibm.websphere.kernel.server.ServerInfoMBean;
import com.ibm.ws.kernel.feature.FeatureProvisioner;

public class PhoneHomeDataCollector  {
	
	/*
	 
    Anonymised identifier of the server instance. Likely a hash of host, install dir, usr dir server name.
    Product Edition - done
    Product Version - done
    Indicator that this is tWAS - done
    List of features in the server - done
    Java vendor and version info - done
    Number of visible cores - done; TODO check if this is the best metric (stack overflow tells me with hyperthreading you'll get more than reality). I considered OSHi but am trying to avoid libraries
    If it is in a container or not
    If there is a stack product installed - set up for stack products to tell us - done

	 */
	
	public Map<String,String> getData() {
		Map<String,String> data = new HashMap<String,String>();
		
		data.put("uniqueId", uniqueID.toString());
		data.put("productEdition", productEdition);
		data.put("productVersion", productVersion);
		data.put("libertyOrTwas", libertyOrTwas);
		data.put("installedFeatures", String.join(",", installedFeatures));
		data.put("javaVersion", javaVersion);
		data.put("javaVMInfo", javaVMInfo);
		data.put("javaRuntimeInfo", javaRuntimeInfo);
		data.put("visibleCores", ""+visibleCores);
		data.put("stackProducts", stackProducts);
		
		return data;
	}
	
	private final HashedString uniqueID;
	
	private final List<PhoneHomeDataSource> dataSources;
	
	private final String javaVersion = System.getProperty("java.version");
	private final String javaVMInfo = System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version");
	private final String javaRuntimeInfo = System.getProperty("java.runtime.name") + " " + System.getProperty("java.runtime.version");
	
	private final Set<String> installedFeatures = new HashSet<String>();

	private final String libertyOrTwas = "liberty";
	
	private final String productVersion;
	private final String productEdition;
	
	private final int visibleCores;
	
	private final String stackProducts;
	
	
	public PhoneHomeDataCollector(List<PhoneHomeDataSource> dataSources, FeatureProvisioner featureProvisioner, 
			ServerInfoMBean serverInfo) throws Exception {
		
		uniqueID = new HashedString(serverInfo.getInstallDirectory()); //placeholder. 
		
		this.dataSources = dataSources;
		installedFeatures.addAll(featureProvisioner.getInstalledFeatures());
		productVersion = serverInfo.getLibertyVersion();
		productEdition = Boolean.valueOf(System.getProperty("com.ibm.ws.beta.edition")) ? "Beta" : "Release";
		
		visibleCores = Runtime.getRuntime().availableProcessors();
		
		stackProducts = dataSources.stream().filter(x -> x.getData().containsKey("stack product name"))
		    .map(x -> x.getData().get("stack product name")).collect(Collectors.joining(","));
	}

	

}
