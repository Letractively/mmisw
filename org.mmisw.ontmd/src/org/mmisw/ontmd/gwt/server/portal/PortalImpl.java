package org.mmisw.ontmd.gwt.server.portal;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mmisw.iserver.core.IServer;
import org.mmisw.iserver.core.Server;
import org.mmisw.iserver.gwt.client.rpc.AppInfo;
import org.mmisw.iserver.gwt.client.rpc.EntityInfo;
import org.mmisw.iserver.gwt.client.rpc.MetadataBaseInfo;
import org.mmisw.iserver.gwt.client.rpc.OntologyInfo;
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
	
	private PortalBaseInfo baseInfo = null;
	
	
	private IServer iserver = Server.getInstance();


	
	
	public PortalImpl() {
		log.info("initializing " +appInfo.getAppName()+ "...");
		appInfo.setVersion(
				Config.Prop.VERSION.getValue()+ " (" +
				Config.Prop.BUILD.getValue()  + ")"
		);

		log.info(appInfo.toString());
	}

	public AppInfo getAppInfo() {
		return appInfo;
	}
	

	public PortalBaseInfo getBaseInfo() {
		if ( baseInfo == null ) {
			prepareBaseInfo();
		}
		return baseInfo;
	}
	
	private void prepareBaseInfo() {
		log.info("preparing base info ...");
		

		log.info("preparing base info ... Done.");
	}
	
	
	
	public List<OntologyInfo> getAllOntologies(boolean includePriorVersions) throws Exception {
		return iserver.getAllOntologies(includePriorVersions);
	}
	
	
	public List<EntityInfo> getEntities(String ontologyUri) {
		return iserver.getEntities(ontologyUri);
	}

	
	public MetadataBaseInfo getMetadataBaseInfo(boolean includeVersion) {
		String resourceTypeClassUri = Config.Prop.RESOURCE_TYPE_CLASS.getValue();
		String authorityClassUri = Config.Prop.AUTHORITY_CLASS.getValue();
		
		return iserver.getMetadataBaseInfo(includeVersion, resourceTypeClassUri, authorityClassUri);
	}
	
	
	public OntologyInfo getOntologyContents(OntologyInfo ontologyInfo) {
		return iserver.getOntologyContents(ontologyInfo);
	}
}