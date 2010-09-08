package org.mmisw.orrportal.gwt.server;

import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mmisw.orrclient.IOrrClient;
import org.mmisw.orrclient.OrrClientConfiguration;
import org.mmisw.orrclient.gwt.client.rpc.CreateOntologyInfo;
import org.mmisw.orrclient.gwt.client.rpc.CreateOntologyResult;
import org.mmisw.orrclient.gwt.client.rpc.CreateUpdateUserAccountResult;
import org.mmisw.orrclient.gwt.client.rpc.InternalOntologyResult;
import org.mmisw.orrclient.gwt.client.rpc.LoginResult;
import org.mmisw.orrclient.gwt.client.rpc.MetadataBaseInfo;
import org.mmisw.orrclient.gwt.client.rpc.RegisterOntologyResult;
import org.mmisw.orrclient.gwt.client.rpc.RegisteredOntologyInfo;
import org.mmisw.orrclient.gwt.client.rpc.ResetPasswordResult;
import org.mmisw.orrclient.gwt.client.rpc.ResolveUriResult;
import org.mmisw.orrclient.gwt.client.rpc.SparqlQueryInfo;
import org.mmisw.orrclient.gwt.client.rpc.SparqlQueryResult;
import org.mmisw.orrclient.gwt.client.rpc.TempOntologyInfo;
import org.mmisw.orrclient.gwt.client.rpc.UnregisterOntologyResult;
import org.mmisw.orrclient.gwt.client.rpc.UserInfoResult;
import org.mmisw.orrclient.gwt.client.rpc.vine.RelationInfo;
import org.mmisw.orrportal.gwt.client.rpc.PortalBaseInfo;


/**
 * A proxy object that creates, configures and interacts with the OrrClient object.
 * 
 * @author Carlos Rueda
 * @version $Id$
 */
public class OrrClientProxy  {

	private static OrrClientProxy instance = null;
	
	/**
	 * Returns the instance of this class, note the it will be null 
	 * if {@link #createInstance(String, String)} has not been called.
	 * @return
	 */
	public static OrrClientProxy getInstance() {
		return instance;
	}
	
	/**
	 * Creates the instance of this class, if not already created.
	 * It uses PortalConfig.
	 * 
	 * @return the instance
	 * @throws IllegalStateException if instance already created.
	 */
	public static OrrClientProxy createInstance() {
		if ( instance != null ) {
			throw new IllegalStateException(OrrClientProxy.class.getName()+ " instance already created");
		}
		instance = new OrrClientProxy();
		return instance;
	}

	
	private final Log log = LogFactory.getLog(OrrClientProxy.class);
	
	private PortalBaseInfo portalBaseInfo = null;
	
	
	private IOrrClient orrClient;
	

	/** 
	 * Creates this object 
	 */
	private OrrClientProxy() {
		log.info("initializing " +getClass().getSimpleName()+ "...");
		
		// parameters from our config object for the OrrClientConfiguration:
		String ontServiceUrl = PortalConfig.Prop.ONT_SERVICE_URL.getValue();
		String previewDirectory = PortalConfig.Prop.PREVIEW_DIR.getValue();
		String voc2rdfDirectory = PortalConfig.Prop.VOC2RDF_DIR.getValue();
		String mailUser = PortalConfig.Prop.MAIL_USER.getValue();
		String mailPassword = PortalConfig.Prop.MAIL_PASSWORD.getValue();
		
		// create OrrClientConfiguration and initialize orrclient library:
		OrrClientConfiguration occ = new OrrClientConfiguration();
		occ.setOntServiceUrl(ontServiceUrl);
		occ.setPreviewDirectory(previewDirectory);
		occ.setVoc2rdfDirectory(voc2rdfDirectory);
		occ.setMailUser(mailUser);
		occ.setMailPassword(mailPassword);
		orrClient = IOrrClient.Manager.init(occ);
		
		log.info("Using: " +orrClient.getAppInfo());
	}

	public PortalBaseInfo getBaseInfo() {
		if ( portalBaseInfo == null ) {
			prepareBaseInfo();
		}
		return portalBaseInfo;
	}
	
	private void prepareBaseInfo() {
		log.info("base info: preparing...");
		
		portalBaseInfo = new PortalBaseInfo();
		
		portalBaseInfo.setAppServerUrl(PortalConfig.Prop.APPSERVER_HOST.getValue());
		portalBaseInfo.setOntServiceUrl(PortalConfig.Prop.ONT_SERVICE_URL.getValue());
		portalBaseInfo.setOntbrowserServiceUrl(PortalConfig.Prop.ONTBROWSER_SERVICE_URL.getValue());

		portalBaseInfo.setGaUaNumber(PortalConfig.Prop.GA_UA_NUMBER.getValue());

		log.info("base info: done.");
	}
	
	
	
	public List<RegisteredOntologyInfo> getAllOntologies(boolean includePriorVersions) throws Exception {
		return orrClient.getAllOntologies(includePriorVersions);
	}
	
	public ResolveUriResult resolveUri(String uri) {
		return orrClient.resolveUri(uri);
	}

	/**
	 * Gets an ontology from the registry.
	 */
	public RegisteredOntologyInfo getOntologyInfo(String ontologyUri) {
		return orrClient.getOntologyInfo(ontologyUri);
	}
	
	
	public MetadataBaseInfo getMetadataBaseInfo(boolean includeVersion) {
		String resourceTypeClassUri = PortalConfig.Prop.RESOURCE_TYPE_CLASS.getValue();
		String authorityClassUri = PortalConfig.Prop.AUTHORITY_CLASS.getValue();
		
		return orrClient.getMetadataBaseInfo(includeVersion, resourceTypeClassUri, authorityClassUri);
	}
	
	
	public RegisteredOntologyInfo getOntologyContents(RegisteredOntologyInfo ontologyInfo, String version) {
		return orrClient.getOntologyContents(ontologyInfo, version);
	}

	public CreateOntologyResult createOntology(CreateOntologyInfo createOntologyInfo) {
		return orrClient.createOntology(createOntologyInfo);
	}
	
	
	public RegisterOntologyResult registerOntology(CreateOntologyResult createOntologyResult, LoginResult loginResult) {
		return orrClient.registerOntology(createOntologyResult, loginResult);
	}
	
	
	public TempOntologyInfo getTempOntologyInfo(
			String fileType, String uploadResults, boolean includeContents,
			boolean includeRdf) {
		return orrClient.getTempOntologyInfo(fileType, uploadResults, includeContents, includeRdf);
	}
	
	
	////////////////////////////////////////////////////////////////////////////////////////////
	// VINE:
	

	public List<RelationInfo> getDefaultVineRelationInfos() {
		return orrClient.getDefaultVineRelationInfos();
	}
	
	// :VINE
	////////////////////////////////////////////////////////////////////////////////////////////

	
	// Search:
	
	public SparqlQueryResult runSparqlQuery(SparqlQueryInfo query) {
		return orrClient.runSparqlQuery(query);
	}
	
	
	// login
	
	public LoginResult authenticateUser(String userName, String userPassword) {
		return orrClient.authenticateUser(userName, userPassword);
	}
	
	public ResetPasswordResult resetUserPassword(String username) {
		return orrClient.resetUserPassword(username);
	}

	public UserInfoResult getUserInfo(String username) {
		return orrClient.getUserInfo(username);
	}
	
	public CreateUpdateUserAccountResult createUpdateUserAccount(Map<String,String> values) {
		return orrClient.createUpdateUserAccount(values);
	}
	
	
	/////////////////////////////////////////////////////////////////////////////////////////////
	// OOI CI semantic prototype
	public RegisterOntologyResult registerOntologyDirectly(
			LoginResult loginResult, 
			RegisteredOntologyInfo registeredOntologyInfo,
			CreateOntologyInfo createOntologyInfo, 
			String graphId
	) {
		return orrClient.registerOntologyDirectly(loginResult, registeredOntologyInfo, createOntologyInfo, graphId);
	}

	
	public InternalOntologyResult prepareUsersOntology(LoginResult loginResult) {
		return orrClient.prepareUsersOntology(loginResult);
	}
	
	public InternalOntologyResult createGroupsOntology(LoginResult loginResult) {
		return orrClient.createGroupsOntology(loginResult);
	}
	
	
	public UnregisterOntologyResult unregisterOntology(LoginResult loginResult, RegisteredOntologyInfo oi) {
		return orrClient.unregisterOntology(loginResult, oi);
	}
}
