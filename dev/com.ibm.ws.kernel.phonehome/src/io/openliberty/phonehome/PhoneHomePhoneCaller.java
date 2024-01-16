package io.openliberty.phonehome;

import java.util.Map;

public class PhoneHomePhoneCaller {

	public static void sendData(Map<String, String> data) {
		//Dummy code for now
		
		for (String key : data.keySet()) {
			System.out.println(key + ": " + data.get(key));
		}
	}
	
}
