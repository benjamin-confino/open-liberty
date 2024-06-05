package io.openliberty.microprofile.telemetry20.logging.internal;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.logging.LogRecord;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.util.tracker.ServiceTracker;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.ws.collector.Collector;
import com.ibm.ws.collector.Target;
import com.ibm.ws.logging.collector.CollectorJsonUtils;
import com.ibm.wsspi.collector.manager.Handler;

import io.openliberty.microprofile.telemetry.internal.common.info.OpenTelemetryInfo;
import io.openliberty.microprofile.telemetry.internal.interfaces.OpenTelemetryAccessor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.api.GlobalOpenTelemetry;
//import io.opentelemetry.exporter.logging.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

import com.ibm.websphere.logging.WsLevel;
import com.ibm.websphere.logging.hpel.LogRecordContext;
import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.ws.collector.Collector;
import com.ibm.ws.collector.Target;
import com.ibm.ws.logging.collector.CollectorConstants;
import com.ibm.ws.logging.collector.CollectorJsonHelpers;
import com.ibm.ws.logging.collector.CollectorJsonUtils;
import com.ibm.ws.logging.collector.LogFieldConstants;
import com.ibm.ws.logging.data.KeyValuePair;
import com.ibm.ws.logging.data.KeyValuePairList;
import com.ibm.ws.logging.data.LogTraceData;
import com.ibm.wsspi.collector.manager.Handler;

import io.openliberty.microprofile.telemetry.internal.common.constants.OpenTelemetryConstants;
import io.openliberty.microprofile.telemetry.internal.common.info.OpenTelemetryInfo;
import io.openliberty.microprofile.telemetry.internal.interfaces.OpenTelemetryAccessor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.SemanticAttributes;

@Component(name = OpenTelemetryHandler.COMPONENT_NAME, service = {
		Handler.class }, configurationPolicy = ConfigurationPolicy.OPTIONAL, property = { "service.vendor=IBM" })
public class OpenTelemetryHandler extends Collector {

	private static final TraceComponent tc = Tr.register(OpenTelemetryHandler.class);

	public static final String COMPONENT_NAME = "io.openliberty.microprofile.telemetry20.logging.internal.OpenTelemetryHandler";


	private OpenTelemetry openTelemetry = null;
	private int counter = 0;

	@Override
	@Reference(name = EXECUTOR_SERVICE, service = ExecutorService.class)
	protected void setExecutorService(ServiceReference<ExecutorService> executorService) {
		executorServiceRef.setReference(executorService);
	}

	@Override
	protected void unsetExecutorService(ServiceReference<ExecutorService> executorService) {
		executorServiceRef.unsetReference(executorService);
	}

	@Override
	@Activate
	protected void activate(ComponentContext cc, Map<String, Object> configuration) {
		System.out.println("IN LOGGINGACTIVATE!");
		
	
		 System.out.println("Getting OTEL SERVER: " + OpenTelemetryAccessor.getOpenTelemetryInfo("SERVER").getOpenTelemetry());

        super.activate(cc, configuration);
        
	}	
	
	@Override
	@Deactivate
	protected void deactivate(ComponentContext cc, int reason) {
		// super.deactivate(cc, reason);
	}

	@Override
	@Modified
	protected void modified(Map<String, Object> configuration) {
		super.modified(configuration);
		validateSources(configuration);
	}

	private void validateSources(Map<String, Object> config) {
		//System.out.println("IN VALIDATE");
		if (config.containsKey(SOURCE_LIST_KEY)) {
			//System.out.println("VALIDATE_CONFIG");
			String[] sourceList = (String[]) config.get(SOURCE_LIST_KEY);
			if (sourceList != null) {
				for (String source : sourceList) {
					if (getSourceName(source.trim()).isEmpty()) {
						Tr.warning(tc, "LOGSTASH_SOURCE_UNKNOWN", source);
					}
				}
			}
		}
	}

	@Override
	public String getHandlerName() {
		return COMPONENT_NAME;
	}

	@Override
	public Object formatEvent(String source, String location, Object event, String[] tags, int maxFieldLength) {
		
		
		
		 String eventType = CollectorJsonUtils.getEventType(source, location);
		    if (eventType.equals(CollectorConstants.MESSAGES_LOG_EVENT_TYPE)) {
		        LogTraceData logData = (LogTraceData) event;
		        if (logData.getMessage().contains("scopeInfo:")) {
		            return null;
		        }
		        
			    AttributesBuilder attributes = Attributes.builder();

			    OpenTelemetry otelInstance = null;
		        ArrayList<KeyValuePair> extensions = null;
		        KeyValuePairList kvpl = null;
		        kvpl = logData.getExtensions();
		        if (kvpl != null) {
		            if (kvpl.getKey().equals(LogFieldConstants.EXTENSIONS_KVPL)) {
		                extensions = kvpl.getList();
		                for (KeyValuePair k : extensions) {
		                    String extKey = k.getKey();
		                    if (extKey.endsWith(CollectorJsonHelpers.INT_SUFFIX)) {
		                        attributes.put(extKey,  k.getIntValue());
		                    } else if (extKey.endsWith(CollectorJsonHelpers.FLOAT_SUFFIX)) {
		                        attributes.put(extKey, k.getFloatValue());
		                    } else if (extKey.endsWith(CollectorJsonHelpers.LONG_SUFFIX)) {
		                        attributes.put(extKey, k.getLongValue());
		                    } else if (extKey.endsWith(CollectorJsonHelpers.BOOL_SUFFIX)) {
		                        attributes.put(extKey, k.getBooleanValue());
		                    } else {
		                        attributes.put(extKey, k.getStringValue());
		                    }
		                    
		                    if(extKey.equals("ext_appName")) {
		                    	String appName = k.getStringValue();
		                    	if(!appName.contains("io.openliberty") && !appName.contains("com.ibm.ws")) {
		                    		otelInstance = OpenTelemetryAccessor.getOpenTelemetryInfo(appName).getOpenTelemetry();
		                    		System.out.println("Getting app instance! " + appName  + " -- " + otelInstance); 
		                    	}
		                    }
		                }
		            }
		        }
		        
		        if(otelInstance == null) {
            		otelInstance = OpenTelemetryAccessor.getOpenTelemetryInfo("SERVER").getOpenTelemetry();
		        }
		        
		        LogRecordBuilder builder = otelInstance.getLogsBridge().loggerBuilder(OpenTelemetryConstants.INSTRUMENTATION_NAME).build().logRecordBuilder();
		        mapLogRecord(builder, logData, eventType, attributes);
		        builder.emit();
		    }
		
		return null;
	}

	@Override
	public Target getTarget() {
		// TODO Auto-generated method stub
		return null;
	}

	private void mapLogRecord(LogRecordBuilder builder, LogTraceData logData, String eventType, AttributesBuilder attributes) {
        boolean isMessageEvent = eventType.equals(CollectorConstants.MESSAGES_LOG_EVENT_TYPE);
	    
	    // message
	    String message = logData.getMessage();
	    builder.setBody(message);
	    
	    // time
	    builder.setTimestamp(logData.getDatetime(), TimeUnit.MILLISECONDS);
	    
	    // level
	    String loglevel = logData.getLoglevel();
	    builder.setSeverity(levelToSeverity(loglevel));
	    
	    String logSeverity = logData.getSeverity();
	    builder.setSeverityText(logSeverity);
	    
	    //AttributesBuilder attributes = Attributes.builder();
	    
	    attributes.put(SemanticAttributes.THREAD_NAME, logData.getThreadName());
	    attributes.put(SemanticAttributes.THREAD_ID, logData.getThreadId());
	    
	    //throwable
        String exceptionName = logData.getExceptionName();
        String throwable = logData.getThrowable();
        if (throwable != null) {
            attributes.put(SemanticAttributes.EXCEPTION_MESSAGE, exceptionName);
            attributes.put(SemanticAttributes.EXCEPTION_STACKTRACE, throwable);
        }
	    
	    attributes.put(LogTraceData.getModuleKey(0, isMessageEvent), logData.getModule())
	              .put(LogTraceData.getMessageIdKey(0, isMessageEvent), logData.getMessageId())
	              .put(LogTraceData.getMethodNameKey(0, isMessageEvent), logData.getMethodName())
	              .put(LogTraceData.getClassNameKey(0, isMessageEvent), logData.getClassName())
	              .put(LogTraceData.getSequenceKey(0, isMessageEvent), logData.getSequence());
	    
	    // add extensions
	    ArrayList<KeyValuePair> extensions = null;
        KeyValuePairList kvpl = null;
        kvpl = logData.getExtensions();
        if (kvpl != null) {
            if (kvpl.getKey().equals(LogFieldConstants.EXTENSIONS_KVPL)) {
                extensions = kvpl.getList();
                for (KeyValuePair k : extensions) {
                    String extKey = k.getKey();
                    if (extKey.endsWith(CollectorJsonHelpers.INT_SUFFIX)) {
                        attributes.put(extKey,  k.getIntValue());
                    } else if (extKey.endsWith(CollectorJsonHelpers.FLOAT_SUFFIX)) {
                        attributes.put(extKey, k.getFloatValue());
                    } else if (extKey.endsWith(CollectorJsonHelpers.LONG_SUFFIX)) {
                        attributes.put(extKey, k.getLongValue());
                    } else if (extKey.endsWith(CollectorJsonHelpers.BOOL_SUFFIX)) {
                        attributes.put(extKey, k.getBooleanValue());
                    } else {
                        attributes.put(extKey, k.getStringValue());
                    }
                }
            }
        }
	    
	    builder.setAllAttributes(attributes.build());
	    
	    // span context
	    builder.setContext(Context.current());
	}
	
	  private static Severity levelToSeverity(String level) {
	      if (level.equals(WsLevel.FATAL.toString())) {
	        return Severity.FATAL;
	      }
          if (level.equals(WsLevel.SEVERE.toString()) || level.equals("SystemErr")) {
              return Severity.ERROR;
            }
          if (level.equals(WsLevel.WARNING.toString())) {
              return Severity.WARN;
            }
          if (level.equals(WsLevel.AUDIT.toString())) {
              return Severity.INFO2;
            }
          if (level.equals(WsLevel.INFO.toString())) {
              return Severity.INFO;
            }
          if (level.equals(WsLevel.CONFIG.toString()) || level.equals("SystemOut")) {
              return Severity.DEBUG4;
            }
          if (level.equals(WsLevel.DETAIL.toString())) {
              return Severity.DEBUG3;
            }
          if (level.equals(WsLevel.FINE.toString())) {
              return Severity.DEBUG2;
            }
          if (level.equals(WsLevel.FINER.toString())) {
              return Severity.DEBUG;
            }
          if (level.equals(WsLevel.FINEST.toString())) {
              return Severity.TRACE;
            }
	      return Severity.FATAL;
	    }
	
}