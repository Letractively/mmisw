package org.mmisw.ontmd.gwt.server.portal;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mmisw.iserver.core.IServer;
import org.mmisw.iserver.core.Server;
import org.mmisw.iserver.gwt.client.rpc.AppInfo;
import org.mmisw.iserver.gwt.client.rpc.CreateOntologyInfo;
import org.mmisw.iserver.gwt.client.rpc.CreateOntologyResult;
import org.mmisw.iserver.gwt.client.rpc.EntityInfo;
import org.mmisw.iserver.gwt.client.rpc.LoginResult;
import org.mmisw.iserver.gwt.client.rpc.MetadataBaseInfo;
import org.mmisw.iserver.gwt.client.rpc.RegisteredOntologyInfo;
import org.mmisw.iserver.gwt.client.rpc.TempOntologyInfo;
import org.mmisw.iserver.gwt.client.rpc.RegisterOntologyResult;
import org.mmisw.ontmd.gwt.client.rpc.PortalBaseInfo;
import org.mmisw.ontmd.gwt.server.Config;



/**
 * portal operations. 
 * 
 * @author Carlos Rueda
 * @version $Id$
 */
public class PortalImpl  {

	private final Log log = LogFactory.getLog(PortalImpl.class);
	
	private final AppInfo appInfo = new AppInfo("MMI Portal");
	
	private PortalBaseInfo portalBaseInfo = null;
	
	
	private IServer iserver;


	
	
	public PortalImpl(String ontServiceUrl, String bioportalRestUrl) {
		log.info("initializing " +appInfo.getAppName()+ "...");
		appInfo.setVersion(
				Config.Prop.VERSION.getValue()+ " (" +
				Config.Prop.BUILD.getValue()  + ")"
		);

		log.info(appInfo.toString());
		
		iserver = Server.getInstance(ontServiceUrl, bioportalRestUrl);
	}

	public AppInfo getAppInfo() {
		return appInfo;
	}
	

	public PortalBaseInfo getBaseInfo() {
		if ( portalBaseInfo == null ) {
			prepareBaseInfo();
		}
		return portalBaseInfo;
	}
	
	private void prepareBaseInfo() {
		log.info("preparing base info ...");
		
		portalBaseInfo = new PortalBaseInfo();
		
		portalBaseInfo.setAppServerUrl(Config.Prop.APPSERVER_HOST.getValue());
		portalBaseInfo.setOntServiceUrl(Config.Prop.ONT_SERVICE_URL.getValue());
		portalBaseInfo.setPortalServiceUrl(Config.Prop.PORTAL_SERVICE_URL.getValue());
		portalBaseInfo.setVineServiceUrl(Config.Prop.VINE_SERVICE_URL.getValue());
		portalBaseInfo.setOntbrowserServiceUrl(Config.Prop.ONTBROWSER_SERVICE_URL.getValue());

		log.info("preparing base info ... Done.");
	}
	
	
	
	public List<RegisteredOntologyInfo> getAllOntologies(boolean includePriorVersions) throws Exception {
		return iserver.getAllOntologies(includePriorVersions);
	}
	
	public RegisteredOntologyInfo getOntologyInfo(String ontologyUri) {
		return iserver.getOntologyInfo(ontologyUri);
	}
	
	public List<EntityInfo> getEntities(String ontologyUri) {
		return iserver.getEntities(ontologyUri);
	}

	
	public MetadataBaseInfo getMetadataBaseInfo(boolean includeVersion) {
		String resourceTypeClassUri = Config.Prop.RESOURCE_TYPE_CLASS.getValue();
		String authorityClassUri = Config.Prop.AUTHORITY_CLASS.getValue();
		
		return iserver.getMetadataBaseInfo(includeVersion, resourceTypeClassUri, authorityClassUri);
	}
	
	
	public RegisteredOntologyInfo getOntologyContents(RegisteredOntologyInfo ontologyInfo) {
		return iserver.getOntologyContents(ontologyInfo);
	}

	public CreateOntologyResult createOntology(CreateOntologyInfo createOntologyInfo) {
		return iserver.createOntology(createOntologyInfo);
	}
	
	
	public RegisterOntologyResult registerOntology(CreateOntologyResult createOntologyResult, LoginResult loginResult) {
		return iserver.registerOntology(createOntologyResult, loginResult);
	}
	
	
	public TempOntologyInfo getTempOntologyInfo(String uploadResults, boolean includeContents,
			boolean includeRdf) {
		return iserver.getTempOntologyInfo(uploadResults, includeContents, includeRdf);
	}
}