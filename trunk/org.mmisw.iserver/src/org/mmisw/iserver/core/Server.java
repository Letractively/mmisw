package org.mmisw.iserver.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mmisw.iserver.core.ontmodel.OntModelUtil;
import org.mmisw.iserver.core.util.MailSender;
import org.mmisw.iserver.core.util.OntServiceUtil;
import org.mmisw.iserver.core.util.QueryUtil;
import org.mmisw.iserver.core.util.TempOntologyHelper;
import org.mmisw.iserver.core.util.Util2;
import org.mmisw.iserver.gwt.client.rpc.AppInfo;
import org.mmisw.iserver.gwt.client.rpc.BaseOntologyInfo;
import org.mmisw.iserver.gwt.client.rpc.CreateOntologyInfo;
import org.mmisw.iserver.gwt.client.rpc.CreateOntologyResult;
import org.mmisw.iserver.gwt.client.rpc.CreateUpdateUserAccountResult;
import org.mmisw.iserver.gwt.client.rpc.DataCreationInfo;
import org.mmisw.iserver.gwt.client.rpc.EntityInfo;
import org.mmisw.iserver.gwt.client.rpc.HostingType;
import org.mmisw.iserver.gwt.client.rpc.LoginResult;
import org.mmisw.iserver.gwt.client.rpc.MappingDataCreationInfo;
import org.mmisw.iserver.gwt.client.rpc.MetadataBaseInfo;
import org.mmisw.iserver.gwt.client.rpc.OtherDataCreationInfo;
import org.mmisw.iserver.gwt.client.rpc.InternalOntologyResult;
import org.mmisw.iserver.gwt.client.rpc.PropValue;
import org.mmisw.iserver.gwt.client.rpc.RegisterOntologyResult;
import org.mmisw.iserver.gwt.client.rpc.RegisteredOntologyInfo;
import org.mmisw.iserver.gwt.client.rpc.UnregisterOntologyResult;
import org.mmisw.iserver.gwt.client.rpc.ResetPasswordResult;
import org.mmisw.iserver.gwt.client.rpc.ResolveUriResult;
import org.mmisw.iserver.gwt.client.rpc.SparqlQueryInfo;
import org.mmisw.iserver.gwt.client.rpc.SparqlQueryResult;
import org.mmisw.iserver.gwt.client.rpc.TempOntologyInfo;
import org.mmisw.iserver.gwt.client.rpc.UserInfoResult;
import org.mmisw.iserver.gwt.client.rpc.VocabularyDataCreationInfo;
import org.mmisw.iserver.gwt.client.rpc.CreateOntologyInfo.PriorOntologyInfo;
import org.mmisw.iserver.gwt.client.rpc.vine.RelationInfo;
import org.mmisw.iserver.gwt.client.vocabulary.AttrGroup;
import org.mmisw.ont.JenaUtil2;
import org.mmisw.ont.MmiUri;
import org.mmisw.ont.vocabulary.Omv;
import org.mmisw.ont.vocabulary.OmvMmi;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.Ontology;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDF;

import edu.drexel.util.rdf.JenaUtil;
import edu.drexel.util.rdf.OwlModel;



/**
 * Implementation of IServer. 
 * 
 * @author Carlos Rueda
 * @version $Id$
 */
public class Server implements IServer {
	private static final long serialVersionUID = 1L;
	
	private static final String LISTALL = "?listall";

	/** Ontology URI prefix including root: */
	public static String defaultNamespaceRoot;


	private final AppInfo appInfo = new AppInfo("MMISW IServer");
	private final Log log = LogFactory.getLog(Server.class);

	private File previewDir;
	
	
	private static IServer _instance ;
	
	public static IServer getInstance(String ontServiceUrl, String bioportalRestUrl) {
		if ( _instance == null ) {
			_instance = new Server(ontServiceUrl, bioportalRestUrl);
		}
		
		return _instance;
	}
	
	private Server(String ontServiceUrl, String bioportalRestUrl) {
		defaultNamespaceRoot = ontServiceUrl;
		log.info("basic init " +appInfo.getAppName()+ "...");
		try {
			ServerConfig.getInstance();
			
			appInfo.setVersion(ServerConfig.Prop.VERSION.getValue());
			appInfo.setBuild(ServerConfig.Prop.BUILD.getValue());
					
			log.info(appInfo.toString());
			
			previewDir = new File(ServerConfig.Prop.ONTMD_PREVIEW_DIR.getValue());
			
			ServerConfig.Prop.ONT_SERVICE_URL.setValue(ontServiceUrl);
			log.info("ontServiceUrl = " +ontServiceUrl);
			
			ServerConfig.Prop.BIOPORTAL_REST_URL.setValue(bioportalRestUrl);
			log.info("bioportalRestUrl = " +bioportalRestUrl);
		}
		catch (Throwable ex) {
			log.error("Cannot initialize: " +ex.getMessage(), ex);
		}
	}
	
	public void destroy() {
		log.info(appInfo+ ": destroy called.\n\n");
	}
	
	public AppInfo getAppInfo() {
		return appInfo;
	}
	

	private MetadataBaseInfo metadataBaseInfo = null;
	
	public MetadataBaseInfo getMetadataBaseInfo(
			boolean includeVersion, String resourceTypeClassUri,
			String authorityClassUri) {
		
		log.info("preparing base info ...");
		
		if ( metadataBaseInfo == null ) {
			metadataBaseInfo = new MetadataBaseInfo();
			
//			metadataBaseInfo.setResourceTypeUri(Omv.acronym.getURI());
			metadataBaseInfo.setResourceTypeUri(OmvMmi.hasResourceType.getURI());
			
			metadataBaseInfo.setAuthorityAbbreviationUri(OmvMmi.origMaintainerCode.getURI());
			
			MdHelper.prepareGroups(includeVersion, resourceTypeClassUri, authorityClassUri);
			AttrGroup[] attrGroups = MdHelper.getAttrGroups();
			metadataBaseInfo.setAttrGroups(attrGroups);
			
			metadataBaseInfo.setAuthorityAttrDef(MdHelper.getAuthorityAttrDef());
			
			metadataBaseInfo.setResourceTypeAttrDef(MdHelper.getResourceTypeAttrDef());
			
			metadataBaseInfo.setUriAttrDefMap(MdHelper.getUriAttrDefMap());
			
			
			log.info("preparing base info ... DONE");

		}
		
		return metadataBaseInfo;
	}

	public List<EntityInfo> getEntities(String ontologyUri) {
		if ( log.isDebugEnabled() ) {
			log.debug("getEntities(String) starting");
		}
		try {
			return  QueryUtil.getEntities(ontologyUri, null);
		}
		catch (Exception e) {
			String error = "Error getting entities: " +e.getMessage();
			log.error(error, e);
			return null;
		}
	}

	
	private static RegisteredOntologyInfo _createOntologyInfo(
			String ontologyUri,   // = toks[0];
			String displayLabel,  // = toks[1];
			String type,          // = toks[2];
			String userId,        // = toks[3];
			String contactName,   // = toks[4];
			String versionNumber, // = toks[5];
			String dateCreated,   // = toks[6];
			String userName,      // = toks[7];
			String ontologyId,     // = toks[8];
			
			String unversionedUri,
			String authority,
			String shortName
	) {
		RegisteredOntologyInfo registeredOntologyInfo = new RegisteredOntologyInfo();
		
		registeredOntologyInfo.setUri(ontologyUri);
		registeredOntologyInfo.setDisplayLabel(displayLabel);
		registeredOntologyInfo.setType(type);
		registeredOntologyInfo.setUserId(userId);
		registeredOntologyInfo.setContactName(contactName);
		registeredOntologyInfo.setVersionNumber(versionNumber);
		registeredOntologyInfo.setDateCreated(dateCreated);
		registeredOntologyInfo.setUsername(userName);
		registeredOntologyInfo.setOntologyId(ontologyId, userId);
		
		registeredOntologyInfo.setUnversionedUri(unversionedUri);
		registeredOntologyInfo.setAuthority(authority);
		registeredOntologyInfo.setShortName(shortName);

		setHostingType(registeredOntologyInfo);
		
		return registeredOntologyInfo;
	}
	
	
	/**
	 * Gets the ontologies from the registry as a map { unversionedUri -> list of OntologyInfos }.
	 * Elements in each list are sorted by descending versionNumber.
	 * 
	 *  @param onlyThisUnversionedUri If this is not null, only this URI (assumed to be unversioned) will be considered,
	 *         so the returned map will at most contain just that single key,
	 */
	private Map<String, List<RegisteredOntologyInfo>> getUnversionedToOntologyInfoListMap(String onlyThisUnversionedUri) throws Exception {
		
		// unversionedUri -> list of corresponding OntologyInfos
		Map<String, List<RegisteredOntologyInfo>> unversionedToVersioned = new LinkedHashMap<String, List<RegisteredOntologyInfo>>();
		
		String uri = ServerConfig.Prop.ONT_SERVICE_URL.getValue()+ LISTALL;
		
		if ( log.isDebugEnabled() ) {
			log.debug("getUnversionedToVersioned. uri= " +uri);
		}

		String response = getAsString(uri, Integer.MAX_VALUE);
		
		String[] lines = response.split("\n|\r\n|\r");
		for ( String line : lines ) {
			// remove leading and trailing quote:
			line = line.replaceAll("^'|'$", "");
			String[] toks = line.trim().split("'\\|'");
			
			String ontologyUri =  toks[0];
			String displayLabel  = toks[1];
			String type          = toks[2];
			String userId        = toks[3];
			String contactName   = toks[4];
			String versionNumber = toks[5];
			String dateCreated   = toks[6];
			String userName      = toks[7];
			String ontologyId    = toks[8];

			String unversionedUri;
			String authority;
			String shortName;
			
			if ( OntServiceUtil.isOntResolvableUri(ontologyUri) ) {
				try {
					MmiUri mmiUri = new MmiUri(ontologyUri);
					authority = mmiUri.getAuthority();
					shortName = mmiUri.getTopic();
					unversionedUri = mmiUri.copyWithVersion(null).getOntologyUri();
				}
				catch (URISyntaxException e) {
					// shouldn't happen.
					
					String error = "Shouldn't happen: ont-resolvable URI is not an MmiUri: " 
						+ontologyUri+ "  Error: " +e.getMessage();
					log.error("getUnversionedToOntologyInfoListMap: " +error, e);
					continue;
				}
			}
			else {
				// FIXME  setting and hoc values temporarily
				authority = ontologyUri;
				shortName = "tempshortname";
				unversionedUri = ontologyUri;
			}

			if ( onlyThisUnversionedUri != null && ! onlyThisUnversionedUri.equals(unversionedUri) ) {
				continue;
			}
			
			RegisteredOntologyInfo registeredOntologyInfo = _createOntologyInfo(
					ontologyUri,
					displayLabel,
					type,
					userId,
					contactName,
					versionNumber,
					dateCreated,
					userName,
					ontologyId,
					
					unversionedUri,
					authority,
					shortName
			);


			List<RegisteredOntologyInfo> versionedList = unversionedToVersioned.get(unversionedUri);
			if ( versionedList == null ) {
				versionedList = new ArrayList<RegisteredOntologyInfo>();
				unversionedToVersioned.put(unversionedUri, versionedList);
			}
			versionedList.add(registeredOntologyInfo);
			registeredOntologyInfo.setUnversionedUri(unversionedUri);
			
		}
		
		// sort all lists by descending versionNumber
		Comparator<RegisteredOntologyInfo> comparator = new Comparator<RegisteredOntologyInfo>() {
			public int compare(RegisteredOntologyInfo arg0, RegisteredOntologyInfo arg1) {
				return - arg0.getVersionNumber().compareTo(arg1.getVersionNumber());
			}
		};
		for ( String unversionedUri : unversionedToVersioned.keySet() ) {
			List<RegisteredOntologyInfo> versionedList = unversionedToVersioned.get(unversionedUri);
			Collections.sort(versionedList, comparator);
		}
		
		
		if ( log.isDebugEnabled() ) {
			log.debug("getUnversionedToOntologyInfoListMap: " +unversionedToVersioned.size()+ " ontologies.");
		}


		return unversionedToVersioned;
	}

	
	private static void setHostingType(RegisteredOntologyInfo registeredOntologyInfo) {
		if ( OntServiceUtil.isOntResolvableUri(registeredOntologyInfo.getUri()) ) {
			registeredOntologyInfo.setHostingType(HostingType.FULLY_HOSTED);
		}
		else {
			registeredOntologyInfo.setHostingType(HostingType.RE_HOSTED);
		}
		// TODO: Determine HostingType.INDEXED case.
	}
	
	
	public List<RegisteredOntologyInfo> getAllOntologies(boolean includeAllVersions) throws Exception {
		
		// {unversionedUri -> list of versioned URIs }  for all unversioned URIs ontologies
		Map<String, List<RegisteredOntologyInfo>> unversionedToVersioned = getUnversionedToOntologyInfoListMap(null);
		
		// the list to be returned
		List<RegisteredOntologyInfo> onts = new ArrayList<RegisteredOntologyInfo>();
		
		for ( String unversionedUri : unversionedToVersioned.keySet() ) {

			List<RegisteredOntologyInfo> versionedList = unversionedToVersioned.get(unversionedUri);
			
			// copy first element, ie., most recent ontology version, for the entry in the main list;
			// Note: the Uri of this main entry is set equal to the UnversionedUri property:
			
			RegisteredOntologyInfo mostRecent = versionedList.get(0);
			RegisteredOntologyInfo registeredOntologyInfo = _createOntologyInfo(
					mostRecent.getUnversionedUri(),      // NOTE: UnversionedURI for the URI
					mostRecent.getDisplayLabel(),
					mostRecent.getType(),
					mostRecent.getUserId(),
					mostRecent.getContactName(),
					mostRecent.getVersionNumber(),
					mostRecent.getDateCreated(),
					mostRecent.getUsername(),
					mostRecent.getOntologyId(),
					
					mostRecent.getUnversionedUri(),
					mostRecent.getAuthority(),
					mostRecent.getShortName()
			);

			// if requested, include all versions: 
			if ( includeAllVersions ) {
				registeredOntologyInfo.getPriorVersions().addAll(versionedList);
			}
			
			// add this main entry to returned list:
			onts.add(registeredOntologyInfo);
		}
		
		return onts;
	}
	
	
	public ResolveUriResult resolveUri(String uri) {
		ResolveUriResult resolveUriResult = new ResolveUriResult(uri);
		
		// try ontology:
		RegisteredOntologyInfo roi = getOntologyInfo(uri);
		if ( roi != null && roi.getError() == null ) {
			resolveUriResult.setRegisteredOntologyInfo(roi);
			return resolveUriResult;
		}
		
		// try term:
		_getEntityInfo(uri, resolveUriResult);
		
		return resolveUriResult;
	}
	
	private void _getEntityInfo(String uri, ResolveUriResult resolveUriResult) {
		
		// NOTE that the query needs to be against Ont, and not in this module (as those done by QueryUtil),
		// because the query needs to be against all registered ontologies.
		
		String query = 
			"SELECT ?prop ?value " +
			"WHERE { <" +uri+ "> ?prop ?value . }"
		;
		String format = "csv";
		
		if ( log.isDebugEnabled() ) {
			log.debug(" format=" +format+ " query=[" +query+ "]");
		}
		
		String result;
		try {
			result = OntServiceUtil.runSparqlQuery(query, format, "text/plain");
		}
		catch (Exception e) {
			String error = "Error while dispatching query: " +e.getMessage();
			resolveUriResult.setError(error);
			return;
		}
		
		if ( log.isDebugEnabled() ) {
			log.debug("RESULT=" +result);
		}
		
		boolean ok = false;
		
		if ( ! result.toLowerCase().startsWith("error:") ) {
			EntityInfo entityInfo = new EntityInfo();
			
			String[] lines = result.split("\n|\r\n|\n");
			for (String string : lines) {
				String[] toks = string.split(",", 2);
				if ( toks.length != 2 || ("prop".equals(toks[0]) && "value".equals(toks[1])) ) {
					continue;
				}
				String prop = toks[0];
				String value = toks.length > 1 ? toks[1] : null;
				
				Resource propResource = ResourceFactory.createResource(prop);
				
				PropValue pv = new PropValue();
				pv.setPropName(propResource.getLocalName());
				pv.setPropUri(prop);
				
				boolean valueIsUri = false;
				try {
					URI jUri = new URI(value);  // just check to see whether is a URI
					valueIsUri = jUri.isAbsolute();
				}
				catch (URISyntaxException e) {
					// ignore.
				}
				if ( valueIsUri ) {
					pv.setValueUri(value);
					Resource objResource = ResourceFactory.createResource(value);
					pv.setValueName(objResource.getLocalName());
				}
				else {
					pv.setValueName(value);
				}
				
				entityInfo.getProps().add(pv);
			}
			
			int size = entityInfo.getProps().size();
			if ( size > 0 ) {
				ok = true;
				entityInfo.setUri(uri);
				if ( log.isDebugEnabled() ) {
					log.debug("Added " +size+ " property/value pairs to " +uri);
				}
				resolveUriResult.setEntityInfo(entityInfo);
			}
		}

		if ( ! ok ) {
			try {
				// is it at least a valid URL?
				new URL(uri);
				resolveUriResult.setIsUrl(true);
			}
			catch (MalformedURLException e) {
				resolveUriResult.setIsUrl(false);
			}
		}
	}

	

	public RegisteredOntologyInfo getOntologyInfo(String ontologyUri) {
		
		log.debug("getOntologyInfo: ontologyUri=" +ontologyUri);
		
		String[] toks = ontologyUri.split("\\?");
		ontologyUri = toks[0];
		
		String version = null;
		if ( toks.length > 1 && toks[1].startsWith("version=") ) {
			version = toks[1].substring("version=".length());
		}
		
		log.debug("getOntologyInfo: ontologyUri=" +ontologyUri+ "  version=" +version);
		
		if ( OntServiceUtil.isOntResolvableUri(ontologyUri) ) {
			try {
				MmiUri mmiUri = new MmiUri(ontologyUri);
				return getOntologyInfoFromMmiUri(ontologyUri, mmiUri, version);
			}
			catch (URISyntaxException e) {
				String error = e.getMessage();
				log.error("getOntologyInfo: Error in URI: " +ontologyUri, e);
				RegisteredOntologyInfo oi = new RegisteredOntologyInfo();
				oi.setError(error);
				return oi;
			}
		}
		else {
			// "external" URI
			String unversOntologyUri = ontologyUri;
			try {
				boolean includeAllVersions = true;
				return getOntologyInfoWithVersionParams(ontologyUri, unversOntologyUri, version, includeAllVersions);
			}
			catch (Exception e) {
				String error = e.getMessage();
				log.error("Error getting RegisteredOntologyInfo: " +ontologyUri, e);
				RegisteredOntologyInfo oi = new RegisteredOntologyInfo();
				oi.setError(error);
				return oi;
			}
		}
	}

	private RegisteredOntologyInfo getOntologyInfoFromMmiUri(String ontologyUri, MmiUri mmiUri, String version) {
		try {	
			// get elements associated with the unversioned form of the requested URI:
			String unversOntologyUri = mmiUri.copyWithVersion(null).getOntologyUri();
			boolean includeAllVersions = true;
			return getOntologyInfoWithVersionParams(ontologyUri, unversOntologyUri, version, includeAllVersions);
		}
		catch (Exception e) {
			String error = e.getMessage();
			log.error("getOntologyInfoFromMmiUri: Error.", e);
			RegisteredOntologyInfo oi = new RegisteredOntologyInfo();
			oi.setError(error);
			return oi;
		}
	}
	
	/**
	 * Get info about a registered ontology using unversion and explicit version parameters.
	 * 
	 * @param ontologyUri           original requested URI
	 * @param unversOntologyUri     corresponding unversioned form
	 * @param version               explicit version
	 * @return
	 * @throws Exception
	 */
	private RegisteredOntologyInfo getOntologyInfoWithVersionParams(String ontologyUri, String unversOntologyUri,
			String version, boolean includeAllVersions) throws Exception {

		// first, get list of entries for the requested ontology using the unversioned form as key:
		Map<String, List<RegisteredOntologyInfo>> unversionedToVersioned = getUnversionedToOntologyInfoListMap(unversOntologyUri);

		log.debug("getOntologyInfoWithVersionParams: getUnversionedToOntologyInfoListMap => " +unversionedToVersioned);
		
		if ( unversionedToVersioned.isEmpty() ) {
			return null; // not found
		}
		assert unversionedToVersioned.size() == 1;

		// get the list of ontologies with same unversioned URI
		List<RegisteredOntologyInfo> list = unversionedToVersioned.values().iterator().next();

		// Three cases: 
		//
		//  a) explicit version given: search for exact match using 'version' field
		//
		//  b) unversioned URI request -> just return first entry in list, which is the most recent,
		//     but making the Uri property equal to the UnversionedUri property
		//
		//  c) versioned URI request -> search the list for the exact match using the 'uri' field
		//


		RegisteredOntologyInfo foundRoi = null;
		
		if ( version != null ) {
			log.debug("Server.getOntologyInfoWithVersionParams case a) version = " +version);
			//  a) explicit version given: search for exact match using the 'version' field:
			for ( RegisteredOntologyInfo oi : list ) {
				if ( version.equals(oi.getVersionNumber()) ) {
					foundRoi = oi;
					break;
				}
			}
		}
		else if ( ontologyUri.equals(unversOntologyUri) ) {
			log.debug("Server.getOntologyInfoWithVersionParams case b) unversioned request = " +unversOntologyUri);
			// b) unversioned URI request, eg., http://mmisw.org/ont/seadatanet/qualityFlag
			// just return first entry in list
			
			// do not alter the first entry in the list!
//			RegisteredOntologyInfo oi = list.get(0);
//			oi.setUri(oi.getUnversionedUri());
//			foundRoi = oi;
			
			// here is how it should be done:
			RegisteredOntologyInfo mostRecent = list.get(0);
			foundRoi = _createOntologyInfo(
					mostRecent.getUnversionedUri(),      // NOTE: UnversionedURI for the URI
					mostRecent.getDisplayLabel(),
					mostRecent.getType(),
					mostRecent.getUserId(),
					mostRecent.getContactName(),
					mostRecent.getVersionNumber(),
					mostRecent.getDateCreated(),
					mostRecent.getUsername(),
					mostRecent.getOntologyId(),
					
					mostRecent.getUnversionedUri(),
					mostRecent.getAuthority(),
					mostRecent.getShortName()
			);
			
		}
		else {
			log.debug("Server.getOntologyInfoWithVersionParams case c) versioned request = " +ontologyUri);
			// c) versioned URI request, eg., http://mmisw.org/ont/seadatanet/20081113T205440/qualityFlag
			// Search list for exact match of 'uri' field:
			for ( RegisteredOntologyInfo oi : list ) {
				if ( ontologyUri.equals(oi.getUri()) ) {
					foundRoi = oi;
					break;
				}
			}
		}
		
		if ( foundRoi != null && includeAllVersions ) {
			foundRoi.getPriorVersions().addAll(list);
		}
		
		return foundRoi;
	}

	public RegisteredOntologyInfo getEntities(RegisteredOntologyInfo registeredOntologyInfo) {
		if ( log.isDebugEnabled() ) {
			log.debug("getEntities(RegisteredOntologyInfo) starting");
		}
		
		
		OntModel ontModel;
		try {
			// note, version==null -> not specific version, so get the latest version
			final String version = null;
			ontModel = OntServiceUtil.retrieveModel(registeredOntologyInfo.getUri(), version);
		}
		catch (Exception e) {
			String error = "Error loading model: " +e.getMessage();
			log.error(error, e);
			registeredOntologyInfo.setError(error);
			return registeredOntologyInfo;
		}

		
		try {
			QueryUtil.getEntities(registeredOntologyInfo, ontModel);
		}
		catch (Exception e) {
			String error = "Error loading model: " +e.getMessage();
			log.error(error, e);
			registeredOntologyInfo.setError(error);
			return registeredOntologyInfo;
		}
		
		return registeredOntologyInfo;
	}


	private static String getAsString(String uri, int maxlen) throws Exception {
		HttpClient client = new HttpClient();
	    GetMethod meth = new GetMethod(uri);
	    try {
	        client.executeMethod(meth);

	        if (meth.getStatusCode() == HttpStatus.SC_OK) {
	            return meth.getResponseBodyAsString(maxlen);
	        }
	        else {
	          throw new Exception("Unexpected failure: " + meth.getStatusLine().toString());
	        }
	    }
	    finally {
	        meth.releaseConnection();
	    }
	}
	
	
	
	
	public RegisteredOntologyInfo getOntologyContents(RegisteredOntologyInfo registeredOntologyInfo, String version) {
		
		if ( log.isDebugEnabled() ) {
			log.debug("getOntologyContents(RegisteredOntologyInfo): loading model");
		}
		
		OntModel ontModel;
		try {
			ontModel = OntServiceUtil.retrieveModel(registeredOntologyInfo.getUri(), version);
		}
		catch (Exception e) {
			String error = "Error loading model: " +e.getMessage();
			log.error(error, e);
			registeredOntologyInfo.setError(error);
			return registeredOntologyInfo;
		}

		// Metadata:
		if ( log.isDebugEnabled() ) {
			log.debug("getOntologyContents(RegisteredOntologyInfo): getting metadata");
		}
		MetadataExtractor.prepareOntologyMetadata(metadataBaseInfo, ontModel, registeredOntologyInfo);

		
		// Data
		if ( log.isDebugEnabled() ) {
			log.debug("getOntologyContents(RegisteredOntologyInfo): getting entities");
		}
		
		try {
			QueryUtil.getEntities(registeredOntologyInfo, ontModel);
		}
		catch (Exception e) {
			String error = "Error getting entities: " +e.getMessage();
			log.error(error, e);
			registeredOntologyInfo.setError(error);
			return registeredOntologyInfo;
		}
		
		return registeredOntologyInfo;
	
	}

	
	// TODO this mechanism copied from MmiUri (in ont project).
	private static final Pattern VERSION_PATTERN = 
				Pattern.compile("^\\d{4}(\\d{2}(\\d{2})?)?(T\\d{2})?(\\d{2}(\\d{2})?)?$");


	////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
	
	public CreateOntologyResult createOntology(CreateOntologyInfo createOntologyInfo) {
		
		CreateOntologyResult createOntologyResult = null;
		
		if ( createOntologyInfo.getHostingType() != null ) {
			// use of this attribute indicates to use the new method
			createOntologyResult = createOntology_newMethod(createOntologyInfo);
		}
		else {
			createOntologyResult = createOntology_oldMethod(createOntologyInfo);
		}
		
		return createOntologyResult;
	}

	
	private CreateOntologyResult createOntology_newMethod(CreateOntologyInfo createOntologyInfo) {
		
		final HostingType hostingType = createOntologyInfo.getHostingType();
		
		log.info("createOntology: called. hostingType = " +hostingType);
		CreateOntologyResult createOntologyResult = new CreateOntologyResult();
		
		if ( createOntologyInfo.getMetadataValues() == null ) {
			String error = "Unexpected: createOntologyInfo.getMetadataValues returned null. Please report this bug";
			createOntologyResult.setError(error);
			log.info(error);
			return createOntologyResult;
		}

		createOntologyResult.setCreateOntologyInfo(createOntologyInfo);
		
		switch ( hostingType ) {
			case FULLY_HOSTED:
				return createOntologyFullyHosted(createOntologyInfo, createOntologyResult);
			case RE_HOSTED:
				return createOntologyReHosted(createOntologyInfo, createOntologyResult);
			default: {
				String error = "Hosting type "+hostingType+ " NOT yet implemented.";
				createOntologyResult.setError(error);
				log.info(error);
				return createOntologyResult;
			}
		}
	}
	
	
	private CreateOntologyResult createOntologyFullyHosted(CreateOntologyInfo createOntologyInfo, CreateOntologyResult createOntologyResult) {
		
		Map<String, String> newValues = createOntologyInfo.getMetadataValues();
		assert ( newValues != null ) ;
		
		DataCreationInfo dataCreationInfo = createOntologyInfo.getDataCreationInfo();
		assert ( dataCreationInfo instanceof OtherDataCreationInfo ) ;
		final OtherDataCreationInfo otherDataCreationInfo = (OtherDataCreationInfo) dataCreationInfo;
		final TempOntologyInfo tempOntologyInfo = otherDataCreationInfo.getTempOntologyInfo();

		
		// to check if this is going to be a new submission (if ontologyId == null) or, otherwise, a new version.
		final String ontologyId = createOntologyInfo.getPriorOntologyInfo().getOntologyId();
		
		
		final String namespaceRoot = defaultNamespaceRoot;
		final String orgAbbreviation = createOntologyInfo.getAuthority();
		final String shortName = createOntologyInfo.getShortName();


		if ( orgAbbreviation == null ) {
			// should not happen.
			String error = "missing authority abbreviation";
			log.info(error);
			createOntologyResult.setError(error);
			return createOntologyResult;
		}
		if ( shortName == null ) {
			// should not happen.
			String error = "missing short name";
			log.info(error);
			createOntologyResult.setError(error);
			return createOntologyResult;
		}

		if ( ontologyId == null ) {
			// This is a new submission. We need to check for any conflict with a preexisting
			// ontology in the repository with the same shortName+orgAbbreviation combination
			//
			if ( ! Util2.checkNoPreexistingOntology(namespaceRoot, orgAbbreviation, shortName, createOntologyResult) ) {
				return createOntologyResult;
			}
		}
		else {
			// This is a submission of a *new version* of an existing ontology.
			// We need to check the shortName+orgAbbreviation combination as any changes here
			// would imply a *new* ontology, not a new version.
			//

			BaseOntologyInfo baseOntologyInfo = createOntologyInfo.getBaseOntologyInfo();
			assert baseOntologyInfo instanceof RegisteredOntologyInfo;
			RegisteredOntologyInfo roi = (RegisteredOntologyInfo) baseOntologyInfo;

			String originalOrgAbbreviation = roi.getAuthority();
			String originalShortName = roi.getShortName();

			if ( ! Util2.checkUriKeyCombinationForNewVersion(
					originalOrgAbbreviation, originalShortName, 
					orgAbbreviation, shortName, createOntologyResult) ) {
				return createOntologyResult;
			}
		}
		
		
		////////////////////////////////////////////////////////////////////////////
		// section to create the ontology the base:
		
		// external ontology case: the base ontology is already available, just use it
		// by setting the full path in the createOntologyResult:
			
		String full_path;

		if ( tempOntologyInfo != null ) {
			// new contents were provided. Use that:
			full_path = tempOntologyInfo.getFullPath();
		}
		else {
			// No new contents. Only possible way for this to happen is that this is 
			// a new version of an existing ontology.

			if ( ontologyId != null ) {
				// Just indicate a null full_path; see below.
				full_path = null;
			}
			else {
				// This shouldn't happen!
				String error = "Unexpected: Submission of new ontology, but not contents were provided. " +
				"This should be detected before submission. Please report this bug";
				createOntologyResult.setError(error);
				log.info(error);
				return createOntologyResult;
			}

		}
		createOntologyResult.setFullPath(full_path);

		
		// current date:
		final Date date = new Date(System.currentTimeMillis());
		
		///////////////////////////////////////////////////////////////////
		// creation date:
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		final String creationDate = sdf.format(date);
		

		///////////////////////////////////////////////////////////////////
		// version:
		// Note, if the version is given from the client, then use it
		String version = newValues.get(Omv.version.getURI());
		if ( version != null && version.trim().length() > 0 ) {
			// check that the given version is OK:
			boolean ok = VERSION_PATTERN.matcher(version).find();
			if ( ! ok ) {
				String error = "Given version is invalid: " +version;
				log.info(error);
				createOntologyResult.setError(error);
				return createOntologyResult;
			}
		}
		else {
			// otherwise: assign it here:
			sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
			sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
			version = sdf.format(date);
		}
		
		
		////////////////////////////////////////////
		// load  model

		OntModel model;
		String uriForEmpty;
		String newContentsFileName;

		if ( createOntologyResult.getFullPath() != null ) {
			//
			// new contents to check.
			// Get model from the new contents.
			//
			full_path = createOntologyResult.getFullPath();
			log.info("Loading model: " +full_path);

			File file = new File(full_path);
			
			try {
				model = Util2.loadModelWithCheckingUtf8(file);
			}
			catch ( Throwable ex ) {
				String error = "Unexpected error: " +ex.getClass().getName()+ " : " +ex.getMessage();
				log.info(error);
				createOntologyResult.setError(error);
				return createOntologyResult;
			}
			
			// get original namespace associated with the ontology, if any:
			uriForEmpty = Util2.getDefaultNamespace(model, file, createOntologyResult);
			// 2009-12-21: previously returning error if uriForEmpty==null. Not anymore; see below.
			
			newContentsFileName = file.getName();
		}
		else {
			// NO new contents.
			// Use contents from prior version.
			PriorOntologyInfo priorVersionInfo = createOntologyInfo.getPriorOntologyInfo();
			
			try {
				model = OntServiceUtil.retrieveModel(createOntologyInfo.getUri(), priorVersionInfo.getVersionNumber());
			}
			catch (Exception e) {
				String error = "error while retrieving registered ontology: " +e.getMessage();
				log.info(error, e);
				createOntologyResult.setError(error);
				return createOntologyResult;
			}
			
			uriForEmpty = model.getNsPrefixURI("");
			if ( uriForEmpty == null ) {
				// Shouldn't happen -- we're reading in an already registered version.
				String error = "error while getting URI for empty prefix for a registered version. " +
						"Please report this bug.";
				log.info(error);
				createOntologyResult.setError(error);
				return createOntologyResult;
			}

			// replace ':', '/', or '\' for '_'
			newContentsFileName = uriForEmpty.replaceAll(":|/|\\\\", "_");
		}

			
		final String original_ns_ = uriForEmpty;
		
		String ns_;
		String base_;

		
		final String finalUri = namespaceRoot + "/" +
		orgAbbreviation + "/" +
		version + "/" +
		shortName;

		ns_ = JenaUtil2.appendFragment(finalUri);
		base_ = JenaUtil2.removeTrailingFragment(finalUri);


		log.info("Setting prefix \"\" for URI " + ns_);
		model.setNsPrefix("", ns_);


		if ( original_ns_ != null ) {
			// Update statements  according to the new namespace:
			log.info("createOntologyFullyHosted: original namespace: '" +uriForEmpty+ "'. " +
				"Elements here will be transferred to new namespace " +ns_
			);
			Util2.replaceNameSpace(model, original_ns_, ns_);
		}
		else {
			log.info("createOntologyFullyHosted: no original namespace, so no transfer will be done.");
		}
		

		
		
		/////////////////////////////////////////////////////////////////
		// Is there an existing OWL.Ontology individual?
		// TODO Note that ONLY the first OWL.Ontology individual is considered.
		Resource ontRes = JenaUtil.getFirstIndividual(model, OWL.Ontology);
		List<Statement> prexistStatements = null; 
		if ( ontRes != null ) {
			prexistStatements = new ArrayList<Statement>();
			log.info("Getting pre-existing properties for OWL.Ontology individual: " +ontRes.getURI());
			StmtIterator iter = ontRes.listProperties();
			while ( iter.hasNext() ) {
				Statement st = iter.nextStatement();
				prexistStatements.add(st);
			}	
		}

		
		// The new OntModel that will contain the pre-existing attributes (if any),
		// plus the new and updated attributes:
		final OwlModel newOntModel = new OwlModel(model);
		final Ontology ont_ = newOntModel.createOntology(base_);
		log.info("New ontology created with namespace " + ns_ + " base " + base_);
		newOntModel.setNsPrefix("", ns_);
		
		// set preferred prefixes:
		Map<String, String> preferredPrefixMap = MdHelper.getPreferredPrefixMap();
		for ( String uri : preferredPrefixMap.keySet() ) {
			String prefix = preferredPrefixMap.get(uri);
			newOntModel.setNsPrefix(prefix, uri);
		}
		
		
		// Set internal attributes, which are updated in the newValues map itself
		// so we facilite the processing below:
		newValues.put(Omv.version.getURI(), version);
		
		newValues.put(Omv.creationDate.getURI(), creationDate);
		
		
		// set some properties from the explicit values
		newValues.put(OmvMmi.origMaintainerCode.getURI(), orgAbbreviation);


		//////////////////////////////////////////////////
		// transfer any preexisting attributes, and then remove all properties from
		// pre-existing ontRes so just the new OntModel gets added.
		if ( ontRes != null ) {
			for ( Statement st : prexistStatements ) {
				Property prd = st.getPredicate();

				//
				// Do not tranfer pre-existing/pre-assigned-above attributes
				//
				String newValue = newValues.get(prd.getURI());
				if ( newValue == null || newValue.trim().length() == 0 ) {
					log.info("  Transferring: " +st.getSubject()+ " :: " +prd+ " :: " +st.getObject());
					newOntModel.add(ont_, prd, st.getObject());
				}
				else {
					log.info(" Removing pre-existing values for predicate: " +prd+ " because of new value " +newValue);
					newOntModel.removeAll(ont_, prd, null);
				}
			}	
			
			
			if ( ! createOntologyResult.isPreserveOriginalBaseNamespace() ) {
				
				// 
				// Only, when we're creating a new model, ie., per the new namespace, do the following removals.
				// (If we did this on a model with the same original namespace, we would remove the owl:Ontology 
				// entry altogether and get an "rdf:Description" instead.
				//
				
				log.info("Removing original OWL.Ontology individual");
				ontRes.removeProperties();
				// TODO the following may be unnecesary but doesn't hurt:
				model.remove(ontRes, RDF.type, OWL.Ontology); 
			}
		}

		
		
		///////////////////////////////////////////////////////
		// Update attributes in model:

		Map<String, Property> uriPropMap = MdHelper.getUriPropMap();
		for ( String uri : newValues.keySet() ) {
			String value = newValues.get(uri).trim();
			if ( value.length() > 0 ) {
				Property prop = uriPropMap.get(uri);
				if ( prop == null ) {
					log.info("No property found for uri='" +uri+ "'");
					continue;
				}

				log.info(" Assigning: " +uri+ " = " +value);
				ont_.addProperty(prop, value);
			}
		}
		


		// Set the missing DC attrs that have defined e	equivalent MMI attrs: 
		Util2.setDcAttributes(ont_);
		
		////////////////////////////////////////////////////////////////////////
		// Done with the model. 
		////////////////////////////////////////////////////////////////////////
		
		// Get resulting string:
		String rdf = JenaUtil2.getOntModelAsString(model, "RDF/XML-ABBREV") ;  // XXX newOntModel);
		
		//TODO: pons: print result RDF for testing
		//System.out.println(rdf);
		if ( log.isDebugEnabled() ) {
			if ( createOntologyResult.isPreserveOriginalBaseNamespace() ) {
				log.debug(rdf);
			}
		}
		
		log.debug("createOntology: setting URI: " +base_);
		createOntologyResult.setUri(base_);
		

		// write new contents to a new file under previewDir:
		
		File reviewedFile = new File(previewDir, newContentsFileName);
		createOntologyResult.setFullPath(reviewedFile.getAbsolutePath());

		_writeRdfToFile(rdf, reviewedFile, createOntologyResult);

		// Done.

		return createOntologyResult;
	}


	
	private CreateOntologyResult createOntologyReHosted(CreateOntologyInfo createOntologyInfo, CreateOntologyResult createOntologyResult) {
		
		Map<String, String> newValues = createOntologyInfo.getMetadataValues();
		assert ( newValues != null ) ;
		
		DataCreationInfo dataCreationInfo = createOntologyInfo.getDataCreationInfo();
		assert ( dataCreationInfo instanceof OtherDataCreationInfo ) ;
		final OtherDataCreationInfo otherDataCreationInfo = (OtherDataCreationInfo) dataCreationInfo;
		final TempOntologyInfo tempOntologyInfo = otherDataCreationInfo.getTempOntologyInfo();

		
		// to check if this is going to be a new submission (if ontologyId == null) or, otherwise, a new version.
		final String ontologyId = createOntologyInfo.getPriorOntologyInfo().getOntologyId();
		
		
		////////////////////////////////////////////////////////////////////////////
		// section to create the ontology the base:
		
		// external ontology case: the base ontology is already available, just use it
		// by setting the full path in the createOntologyResult:
			
		String full_path;

		if ( tempOntologyInfo != null ) {
			// new contents were provided. Use that:
			full_path = tempOntologyInfo.getFullPath();
		}
		else {
			// No new contents. Only possible way for this to happen is that this is 
			// a new version of an existing ontology.

			if ( ontologyId != null ) {
				// Just indicate a null full_path; see below.
				full_path = null;
			}
			else {
				// This shouldn't happen!
				String error = "Unexpected: Submission of new ontology, but not contents were provided. " +
				"This should be detected before submission. Please report this bug";
				createOntologyResult.setError(error);
				log.info(error);
				return createOntologyResult;
			}

		}
		createOntologyResult.setFullPath(full_path);

		
		// current date:
		final Date date = new Date(System.currentTimeMillis());
		
		///////////////////////////////////////////////////////////////////
		// creation date:
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		final String creationDate = sdf.format(date);
		

		///////////////////////////////////////////////////////////////////
		// version:
		// Note, if the version is given from the client, then use it
		String version = newValues.get(Omv.version.getURI());
		if ( version != null && version.trim().length() > 0 ) {
			// check that the given version is OK:
			boolean ok = VERSION_PATTERN.matcher(version).find();
			if ( ! ok ) {
				String error = "Given version is invalid: " +version;
				log.info(error);
				createOntologyResult.setError(error);
				return createOntologyResult;
			}
		}
		else {
			// otherwise: assign it here:
			sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
			sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
			version = sdf.format(date);
		}
		
		
		////////////////////////////////////////////
		// load  model

		OntModel model;
		Ontology ont;
		String uriForEmpty;
		String newContentsFileName;

		if ( createOntologyResult.getFullPath() != null ) {
			//
			// new contents to check.
			// Get model from the new contents.
			//
			full_path = createOntologyResult.getFullPath();
			log.info("Loading model: " +full_path);

			File file = new File(full_path);
			
			try {
				model = Util2.loadModelWithCheckingUtf8(file);
			}
			catch ( Throwable ex ) {
				String error = "Unexpected error: " +ex.getClass().getName()+ " : " +ex.getMessage();
				log.info(error);
				createOntologyResult.setError(error);
				return createOntologyResult;
			}
			
			ont = OntModelUtil.getOntology(model);
			
			// get original namespace associated with the ontology, if any:
			uriForEmpty = Util2.getDefaultNamespace(model, file, createOntologyResult);
			// 2009-12-21: previously returning error if uriForEmpty==null. Not anymore; see below.
			
			newContentsFileName = file.getName();
		}
		else {
			// NO new contents.
			// Use contents from prior version.
			PriorOntologyInfo priorVersionInfo = createOntologyInfo.getPriorOntologyInfo();
			
			try {
				model = OntServiceUtil.retrieveModel(createOntologyInfo.getUri(), priorVersionInfo.getVersionNumber());
			}
			catch (Exception e) {
				String error = "error while retrieving registered ontology: " +e.getMessage();
				log.info(error, e);
				createOntologyResult.setError(error);
				return createOntologyResult;
			}
			
			ont = OntModelUtil.getOntology(model);
			if ( ont == null ) {
				// Shouldn't happen -- we're reading in an already registered version.
				String error = "error while getting Ontology resource a registered version. " +
						"Please report this bug.";
				log.info(error);
				createOntologyResult.setError(error);
				return createOntologyResult;
			}
			uriForEmpty = ont.getURI();

			// replace ':', '/', or '\' for '_'
			newContentsFileName = uriForEmpty.replaceAll(":|/|\\\\", "_");
		}

			
		
		final String original_base_ = JenaUtil2.removeTrailingFragment(uriForEmpty);

		// and this is the info for the requested URI:
		final String ontUri = createOntologyInfo.getUri();
		final String ns_ = JenaUtil2.appendFragment(ontUri);
		final String base_ = JenaUtil2.removeTrailingFragment(ontUri);

		log.info("createOntologyReHosted: original namespace: '" +original_base_+ "'");
		if ( ! original_base_.equals(base_) ) {
			// In this re-hosted case, we force the original URI and the new URI to be the same.
			// This may happen only in the case of a submission of a new version.
			String error = "The new base URI (" +base_+ ") is not equal to the registered " +
				"base URI (" +original_base_+ ") "
			;
			log.debug(error);
			createOntologyResult.setError(error);
			return createOntologyResult;
		}
		
		/////////////////////////////////////////////////////////////////
		// If there is an pre-existing Ontology resource, get the associated statements:
		List<Statement> prexistStatements = null; 
		if ( ont != null ) {
			prexistStatements = new ArrayList<Statement>();
			if ( log.isDebugEnabled() ) {
				log.debug("Getting pre-existing properties from Ontology: " +ont.getURI());
			}
			StmtIterator iter = ont.listProperties();
			while ( iter.hasNext() ) {
				Statement st = iter.nextStatement();
				prexistStatements.add(st);
			}	
		}

		
		// The new OntModel that will contain the pre-existing attributes (if any),
		// plus the new and updated attributes:
		final OntModel newOntModel = OntModelUtil.createOntModel(base_, model);
		final Ontology ont_ = OntModelUtil.getOntology(newOntModel);
		if ( log.isDebugEnabled() ) {
			log.debug("New ontology created with namespace " + ns_ + " base " + base_);
		}
		
		// Set internal attributes, which are updated in the newValues map itself
		// so we facilite the processing below:
		newValues.put(Omv.version.getURI(), version);
		newValues.put(Omv.creationDate.getURI(), creationDate);
		

		//////////////////////////////////////////////////
		// transfer any preexisting attributes, and then remove all properties from
		// pre-existing ont so just the new OntModel gets added.
		if ( prexistStatements != null ) {
			for ( Statement st : prexistStatements ) {
				Property prd = st.getPredicate();

				//
				// Do not tranfer pre-existing/pre-assigned-above attributes
				//
				String newValue = newValues.get(prd.getURI());
				if ( newValue == null || newValue.trim().length() == 0 ) {
					if ( log.isDebugEnabled() ) {
						log.debug("  Transferring: " +st.getSubject()+ " :: " +prd+ " :: " +st.getObject());
					}
					newOntModel.add(ont_, prd, st.getObject());
				}
				else {
					if ( log.isDebugEnabled() ) {
						log.debug(" Removing pre-existing values for predicate: " +prd+ " because of new value " +newValue);
					}
					newOntModel.removeAll(ont_, prd, null);
				}
			}	
			
		}

		
		
		///////////////////////////////////////////////////////
		// Update attributes in model:

		Map<String, Property> uriPropMap = MdHelper.getUriPropMap();
		for ( String uri : newValues.keySet() ) {
			String value = newValues.get(uri);
			if ( value != null && value.trim().length() > 0 ) {
				Property prop = uriPropMap.get(uri);
				if ( prop == null ) {
					log.info("No property found for uri='" +uri+ "'");
					continue;
				}

				log.info(" Assigning: " +uri+ " = " +value);
				ont_.addProperty(prop, value);
			}
		}
		


		// Set the missing DC attrs that have defined e	equivalent MMI attrs: 
		Util2.setDcAttributes(ont_);
		
		////////////////////////////////////////////////////////////////////////
		// Done with the model. 
		////////////////////////////////////////////////////////////////////////
		
		// Get resulting string:
		String rdf = JenaUtil2.getOntModelAsString(model, "RDF/XML-ABBREV") ;  // XXX newOntModel);
		
		//TODO remove this print result RDF for testing
		System.out.println(rdf);
		if ( log.isDebugEnabled() ) {
			log.debug(rdf);
		}
		
		log.debug("createOntology: setting URI: " +base_);
		createOntologyResult.setUri(base_);
		

		// write new contents to a new file under previewDir:
		
		File reviewedFile = new File(previewDir, newContentsFileName);
		createOntologyResult.setFullPath(reviewedFile.getAbsolutePath());
		
		_writeRdfToFile(rdf, reviewedFile, createOntologyResult);

		// Done.

		return createOntologyResult;
	}

	
	
	
	private void _writeRdfToFile(String rdf, File reviewedFile, CreateOntologyResult createOntologyResult) {
		PrintWriter os;
		try {
			// #43: Handle non-UTF8 inputs
			// Previously new PrintWriter(reviewedFile) ie., default charset; now "UTF-8":
			os = new PrintWriter(reviewedFile, "UTF-8");
		}
		catch (FileNotFoundException e) {
			log.info("Unexpected: file not found: " +reviewedFile);
			createOntologyResult.setError("Unexpected: file not found: " +reviewedFile);
			return;
		}
		catch (UnsupportedEncodingException e) {
			log.info("Unexpected: cannot create file in UTF-8 encoding: " +reviewedFile);
			createOntologyResult.setError("cannot create file in UTF-8 encoding: " +reviewedFile);
			return;
		}
		StringReader is = new StringReader(rdf);
		try {
			IOUtils.copy(is, os);
			os.flush();
		}
		catch (IOException e) {
			log.info("Unexpected: IO error while writing to: " +reviewedFile);
			createOntologyResult.setError("Unexpected: IO error while writing to: " +reviewedFile);
			return;
		}
	}

	
	// TODO remove when new mechanism is in place.
	@SuppressWarnings("deprecation")
	private CreateOntologyResult createOntology_oldMethod(CreateOntologyInfo createOntologyInfo) {
			
		log.info("createOntology: called.");
		
		CreateOntologyResult createOntologyResult = new CreateOntologyResult();
		
		DataCreationInfo dataCreationInfo = createOntologyInfo.getDataCreationInfo();
		if ( dataCreationInfo == null ) {
			createOntologyResult.setError("No data creation info provided! (please report this bug)");
			return createOntologyResult;
		}
		

		createOntologyResult.setCreateOntologyInfo(createOntologyInfo);
		
		// to check if this is going to be a new submission (if ontologyId == null) or, otherwise, a new version.
		final String ontologyId = createOntologyInfo.getPriorOntologyInfo().getOntologyId();
		
		
		//{pons      pons: sections related with preserveOriginalBaseNamespace
		
		// this flag will be only true in the case where an external ontology is to be registered
		// and the user indicates that the original base namespace be preserved.
		createOntologyResult.setPreserveOriginalBaseNamespace(false);
		
		if ( dataCreationInfo instanceof OtherDataCreationInfo ) {
			OtherDataCreationInfo odci = (OtherDataCreationInfo) dataCreationInfo;
			TempOntologyInfo toi = odci.getTempOntologyInfo();
			createOntologyResult.setPreserveOriginalBaseNamespace(toi != null && toi.isPreserveOriginalBaseNamespace());
		}

		
		if ( ! createOntologyResult.isPreserveOriginalBaseNamespace() ) {
			// Note: if this is the submission of a new version (ontologyId != null) of an "external" (ie, re-hosted)
			// ontology, then set this flag to true
			if ( ontologyId != null && ! OntServiceUtil.isOntResolvableUri(createOntologyInfo.getUri()) ) {
				createOntologyResult.setPreserveOriginalBaseNamespace(true);
				
				// TODO However, note that we're goint to preserve the URI of the given ontology in this submission,
				// which may not coincide with the previous one.
			}
		}
		//}pons

		
		Map<String, String> newValues = createOntologyInfo.getMetadataValues();
		
		////////////////////////////////////////////
		// check for errors
		
		if ( createOntologyResult.getError() != null ) {
			log.info(": error: " +createOntologyResult.getError());
			return createOntologyResult;
		}
		
		
		if ( newValues == null ) {
			String error = "Unexpected: no new values assigned for review. Please report this bug";
			createOntologyResult.setError(error );
			log.info(error);
			return createOntologyResult;
		}
		
		
		final String namespaceRoot = defaultNamespaceRoot;
		
		final String orgAbbreviation = newValues.get(OmvMmi.origMaintainerCode.getURI());
		String shortName = newValues.get(Omv.acronym.getURI());
		// TODO: shortName taken NOT from acronym but from a new field explicitly for the shortName piece

		if ( ! createOntologyResult.isPreserveOriginalBaseNamespace() ) {
			//pons: check the following if regular assignment of namespace
			
			if ( orgAbbreviation == null ) {
				log.info("missing origMaintainerCode");
				createOntologyResult.setError("missing origMaintainerCode");
				return createOntologyResult;
			}
			
			if ( shortName == null ) {
				
				if ( ontologyId == null ) {
					// This is a new submission.
					log.info("missing acronym (to be used as shortName)");
					createOntologyResult.setError("missing acronym (to be used as shortName)");
					return createOntologyResult;
				}
				else {
					// take from previous information
					String originalShortName = createOntologyInfo.getShortName();
					shortName = originalShortName;
				}
			}

			if ( ontologyId == null ) {
				// This is a new submission. We need to check for any conflict with a preexisting
				// ontology in the repository with the same shortName+orgAbbreviation combination
				//
				if ( ! Util2.checkNoPreexistingOntology(namespaceRoot, orgAbbreviation, shortName, createOntologyResult) ) {
					return createOntologyResult;
				}
			}
			else {
				// This is a submission of a *new version* of an existing ontology.
				// We need to check the shortName+orgAbbreviation combination as any changes here
				// would imply a *new* ontology, not a new version.
				//
				
				String originalOrgAbbreviation = createOntologyInfo.getAuthority();
				String originalShortName = createOntologyInfo.getShortName();
				
				if ( ! Util2.checkUriKeyCombinationForNewVersion(
						originalOrgAbbreviation, originalShortName, 
						orgAbbreviation, shortName, createOntologyResult) ) {
					return createOntologyResult;
				}
			}
		}
		// Else: see below, where we obtain the original namespace.
		
		
		////////////////////////////////////////////////////////////////////////////
		// section to create the ontology the base:
		
		if ( dataCreationInfo instanceof VocabularyDataCreationInfo ) {
			// vocabulary (voc2rdf) case:
			
			VocabularyDataCreationInfo vocabularyDataCreationInfo = (VocabularyDataCreationInfo) dataCreationInfo;
			
			_createTempVocabularyOntology(createOntologyInfo, vocabularyDataCreationInfo, createOntologyResult);
			if ( createOntologyResult.getError() != null ) {
				return createOntologyResult;
			}
		}
		else if (  dataCreationInfo instanceof MappingDataCreationInfo ) {
			// mapping (vine case):
			
			MappingDataCreationInfo mappingDataCreationInfo = (MappingDataCreationInfo) dataCreationInfo;
			
			_createTempMappingOntology(createOntologyInfo, mappingDataCreationInfo, createOntologyResult);
			if ( createOntologyResult.getError() != null ) {
				return createOntologyResult;
			}

		}
		else if (  dataCreationInfo instanceof OtherDataCreationInfo) {
			// external ontology case: the base ontology is already available, just use it
			// by setting the full path in the createOntologyResult:
			
			OtherDataCreationInfo otherDataCreationInfo = (OtherDataCreationInfo) dataCreationInfo;
			TempOntologyInfo tempOntologyInfo = otherDataCreationInfo.getTempOntologyInfo();
			
			String full_path;
			
			if ( tempOntologyInfo != null ) {
				// new contents were provided. Use that:
				full_path = tempOntologyInfo.getFullPath();
			}
			else {
				// No new contents. Only possible way for this to happen is that this is 
				// a new version of an existing ontology.
				
				if ( ontologyId != null ) {
					// Just indicate a null full_path; see below.
					full_path = null;
				}
				else {
					// This shouldn't happen!
					String error = "Unexpected: Submission of new ontology, but not contents were provided. " +
					"This should be detected before submission. Please report this bug";
					createOntologyResult.setError(error);
					log.info(error);
					return createOntologyResult;
				}
				
			}
			createOntologyResult.setFullPath(full_path);
		}
		else {
			createOntologyResult.setError("Unexpected creation of " +
					dataCreationInfo.getClass().getSimpleName()+ ". Please report this bug.");
			return createOntologyResult;
		}

		
		// current date:
		final Date date = new Date(System.currentTimeMillis());
		
		///////////////////////////////////////////////////////////////////
		// creation date:
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		final String creationDate = sdf.format(date);
		

		///////////////////////////////////////////////////////////////////
		// version:
		// Note, if the version is given from the client, then use it
		String version = newValues.get(Omv.version.getURI());
		if ( version != null && version.trim().length() > 0 ) {
			// check that the given version is OK:
			boolean ok = VERSION_PATTERN.matcher(version).find();
			if ( ! ok ) {
				String error = "Given version is invalid: " +version;
				log.info(error);
				createOntologyResult.setError(error);
				return createOntologyResult;
			}
		}
		else {
			// otherwise: assign it here:
			sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
			sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
			version = sdf.format(date);
		}
		
		
		////////////////////////////////////////////
		// load  model

		OntModel model;
		String uriForEmpty;
		String newContentsFileName;

		if ( createOntologyResult.getFullPath() != null ) {
			//
			// new contents to check.
			// Get model from the new contents.
			//
			String full_path = createOntologyResult.getFullPath();
			log.info("Loading model: " +full_path);

			File file = new File(full_path);
			
			try {
				model = Util2.loadModelWithCheckingUtf8(file);
			}
			catch ( Throwable ex ) {
				String error = "Unexpected error: " +ex.getClass().getName()+ " : " +ex.getMessage();
				log.info(error);
				createOntologyResult.setError(error);
				return createOntologyResult;
			}
			
			uriForEmpty = Util2.getDefaultNamespace(model, file, createOntologyResult);

			if ( uriForEmpty == null ) {
				String error = "Cannot get base URI for the ontology";
				log.info(error);
				createOntologyResult.setError(error);
				return createOntologyResult;
			}
			
			newContentsFileName = file.getName();
		}
		else {
			// NO new contents.
			// Use contents from prior version.
			PriorOntologyInfo priorVersionInfo = createOntologyInfo.getPriorOntologyInfo();
			
			try {
				model = OntServiceUtil.retrieveModel(createOntologyInfo.getUri(), priorVersionInfo.getVersionNumber());
			}
			catch (Exception e) {
				String error = "error while retrieving registered ontology: " +e.getMessage();
				log.info(error, e);
				createOntologyResult.setError(error);
				return createOntologyResult;
			}
			
			uriForEmpty = model.getNsPrefixURI("");
			if ( uriForEmpty == null ) {
				// Shouldn't happen -- we're reading in an already registered version.
				String error = "error while getting URI for empty prefix for a registered version. " +
						"Please report this bug.";
				log.info(error);
				createOntologyResult.setError(error);
				return createOntologyResult;
			}

			// replace ':', '/', or '\' for '_'
			newContentsFileName = uriForEmpty.replaceAll(":|/|\\\\", "_");
		}

			

		
		log.info("createOntology: using '" +uriForEmpty+ "' as base URI");
		
		final String original_ns_ = uriForEmpty;
		log.info("original namespace: " +original_ns_);

		
		String ns_;
		String base_;

		
		if ( createOntologyResult.isPreserveOriginalBaseNamespace() ) {
			//pons:  just use original namespace
			ns_ = original_ns_;
			base_ = JenaUtil2.removeTrailingFragment(ns_);
			
			///////////////////////////////////////////////////////
			
			if ( ontologyId == null ) {
				// This is a new submission. We need to check for any conflict with a preexisting
				// ontology in the repository with the same URI, base_
				//
				if ( ! Util2.checkNoPreexistingOntology(base_, createOntologyResult) ) {
					return createOntologyResult;
				}
			}
			else {
				// This is a submission of a *new version* of an existing ontology.
				// NO check needed--the given URI (base_) is respected.
			}
			///////////////////////////////////////////////////////
		}
		else {
			
			final String finalUri = namespaceRoot + "/" +
										orgAbbreviation + "/" +
										version + "/" +
										shortName;
										
			ns_ = JenaUtil2.appendFragment(finalUri);
			base_ = JenaUtil2.removeTrailingFragment(finalUri);
			
			
			log.info("Setting prefix \"\" for URI " + ns_);
			model.setNsPrefix("", ns_);
			
			
			// Update statements  according to the new namespace:
			Util2.replaceNameSpace(model, original_ns_, ns_);
			
		}
		
		
		/////////////////////////////////////////////////////////////////
		// Is there an existing OWL.Ontology individual?
		// TODO Note that ONLY the first OWL.Ontology individual is considered.
		Resource ontRes = JenaUtil.getFirstIndividual(model, OWL.Ontology);
		List<Statement> prexistStatements = null; 
		if ( ontRes != null ) {
			prexistStatements = new ArrayList<Statement>();
			log.info("Getting pre-existing properties for OWL.Ontology individual: " +ontRes.getURI());
			StmtIterator iter = ontRes.listProperties();
			while ( iter.hasNext() ) {
				Statement st = iter.nextStatement();
				prexistStatements.add(st);
			}	
		}

		
		// The new OntModel that will contain the pre-existing attributes (if any),
		// plus the new and updated attributes:
		final OwlModel newOntModel = new OwlModel(model);
		final Ontology ont_ = newOntModel.createOntology(base_);
		log.info("New ontology created with namespace " + ns_ + " base " + base_);
		newOntModel.setNsPrefix("", ns_);
		
		// set preferred prefixes:
		Map<String, String> preferredPrefixMap = MdHelper.getPreferredPrefixMap();
		for ( String uri : preferredPrefixMap.keySet() ) {
			String prefix = preferredPrefixMap.get(uri);
			newOntModel.setNsPrefix(prefix, uri);
		}
		
		
		// Set internal attributes, which are updated in the newValues map itself
		// so we facilite the processing below:
		newValues.put(Omv.uri.getURI(), base_);
		newValues.put(Omv.version.getURI(), version);
		
		newValues.put(Omv.creationDate.getURI(), creationDate);


		//////////////////////////////////////////////////
		// transfer any preexisting attributes, and then remove all properties from
		// pre-existing ontRes so just the new OntModel gets added.
		if ( ontRes != null ) {
			for ( Statement st : prexistStatements ) {
				Property prd = st.getPredicate();

				//
				// Do not tranfer pre-existing/pre-assigned-above attributes
				//
				String newValue = newValues.get(prd.getURI());
				if ( newValue == null || newValue.trim().length() == 0 ) {
					log.info("  Transferring: " +st.getSubject()+ " :: " +prd+ " :: " +st.getObject());
					newOntModel.add(ont_, prd, st.getObject());
				}
				else {
					log.info(" Removing pre-existing values for predicate: " +prd+ " because of new value " +newValue);
					newOntModel.removeAll(ont_, prd, null);
				}
			}	
			
			
			if ( ! createOntologyResult.isPreserveOriginalBaseNamespace() ) {
				
				// 
				// Only, when we're creating a new model, ie., per the new namespace, do the following removals.
				// (If we did this on a model with the same original namespace, we would remove the owl:Ontology 
				// entry altogether and get an "rdf:Description" instead.
				//
				
				log.info("Removing original OWL.Ontology individual");
				ontRes.removeProperties();
				// TODO the following may be unnecesary but doesn't hurt:
				model.remove(ontRes, RDF.type, OWL.Ontology); 
			}
		}

		
		
		///////////////////////////////////////////////////////
		// Update attributes in model:

		Map<String, Property> uriPropMap = MdHelper.getUriPropMap();
		for ( String uri : newValues.keySet() ) {
			String value = newValues.get(uri).trim();
			if ( value.length() > 0 ) {
				Property prop = uriPropMap.get(uri);
				if ( prop == null ) {
					log.info("No property found for uri='" +uri+ "'");
					continue;
				}

				log.info(" Assigning: " +uri+ " = " +value);
				ont_.addProperty(prop, value);
			}
		}
		


		// Set the missing DC attrs that have defined e	equivalent MMI attrs: 
		Util2.setDcAttributes(ont_);
		
		////////////////////////////////////////////////////////////////////////
		// Done with the model. 
		////////////////////////////////////////////////////////////////////////
		
		// Get resulting string:
		String rdf = JenaUtil2.getOntModelAsString(model, "RDF/XML-ABBREV") ;  // XXX newOntModel);
		
		//TODO: pons: print result RDF for testing
		System.out.println(rdf);
		if ( log.isDebugEnabled() ) {
			if ( createOntologyResult.isPreserveOriginalBaseNamespace() ) {
				log.debug(rdf);
			}
		}
		
		log.debug("createOntology: setting URI: " +base_);
		createOntologyResult.setUri(base_);
		

		// write new contents to a new file under previewDir:
		
		File reviewedFile = new File(previewDir, newContentsFileName);
		createOntologyResult.setFullPath(reviewedFile.getAbsolutePath());

		_writeRdfToFile(rdf, reviewedFile, createOntologyResult);

		// Done.

		return createOntologyResult;
	}

	/**
	 * This creates a new vocabulary ontology under the previewDir directory with the given info.
	 * @param createOntologyInfo
	 * @param vocabularyCreationInfo
	 * @param createVocabResult 
	 */
	private void _createTempVocabularyOntology(
			CreateOntologyInfo createOntologyInfo,
			VocabularyDataCreationInfo vocabularyCreationInfo,
			CreateOntologyResult createVocabResult
	) {
		
		VocabCreator vocabCreator;
		try {
			vocabCreator = new VocabCreator(createOntologyInfo, vocabularyCreationInfo);
		}
		catch (Exception e) {
			String error = "Error in conversion to RDF: " +e.getMessage();
			log.error(error, e);
			createVocabResult.setError(error);
			return;
		}
		
		vocabCreator.createOntology(createVocabResult);
	}

	
	/**
	 * This creates a new mapping ontology under the previewDir directory with the given info.
	 * @param createOntologyInfo
	 * @param mappingDataCreationInfo
	 * @param createVocabResult 
	 */
	private void _createTempMappingOntology(
			CreateOntologyInfo createOntologyInfo,
			MappingDataCreationInfo mappingDataCreationInfo,
			CreateOntologyResult createVocabResult
	) {

		MappingOntologyCreator ontCreator;
		try {
			ontCreator = new MappingOntologyCreator(createOntologyInfo, mappingDataCreationInfo);
		}
		catch (Exception e) {
			String error = "Error creating mapping ontology: " +e.getMessage();
			log.error(error, e);
			createVocabResult.setError(error);
			return;
		}
		
		ontCreator.createOntology(createVocabResult);
	}

	
	public RegisterOntologyResult registerOntology(CreateOntologyResult createOntologyResult, LoginResult loginResult) {
		RegisterOntologyResult registerOntologyResult = null;
		
		if ( createOntologyResult.getCreateOntologyInfo().getHostingType() != null ) {
			// use of this attribute indicates to use the new method
			registerOntologyResult = registerOntology_newMethod(createOntologyResult, loginResult);
		}
		else {
			registerOntologyResult = registerOntology_oldMethod(createOntologyResult, loginResult);
		}
		
		return registerOntologyResult;
	
	}

	public RegisterOntologyResult registerOntology_newMethod(CreateOntologyResult createOntologyResult, LoginResult loginResult) {
		final HostingType hostingType = createOntologyResult.getCreateOntologyInfo().getHostingType();
		
		log.info("registerOntology: called. hostingType = " +hostingType);
		RegisterOntologyResult registerOntologyResult = new RegisterOntologyResult();
		
		switch ( hostingType ) {
			case FULLY_HOSTED:
				return registerOntologyFullyHosted(createOntologyResult, registerOntologyResult, loginResult);
			case RE_HOSTED:
				return registerOntologyReHosted(createOntologyResult, registerOntologyResult, loginResult);
			default: {
				String error = "Hosting type "+hostingType+ " NOT yet implemented.";
				registerOntologyResult.setError(error);
				log.info(error);
				return registerOntologyResult;
			}
		}

	}
	
	public RegisterOntologyResult registerOntologyFullyHosted(CreateOntologyResult createOntologyResult, RegisterOntologyResult registerOntologyResult, LoginResult loginResult) {
	
		String full_path = createOntologyResult.getFullPath();
		
		log.info("registerOntology: Reading in temporary file: " +full_path);
		
		File file = new File(full_path);
		
		// Get resulting model:
		String rdf;
		try {
//			rdf = Util2.readRdf(file);
			rdf = Util2.readRdfWithCheckingUtf8(file);

			// conversion to UTF-8: the following code was not finally enabled
//			ReadFileResult result = Utf8Util.readFileWithConversionToUtf8(file);
//			if ( result.getError() != null ) {
//				String error = "Cannot read RDF model: " +full_path+ " : " +result.getError()+ "\n"
//					+ result.getLogInfo();
//				log.info(error);
//				registerOntologyResult.setError(error);
//				return registerOntologyResult;
//			}
//			System.out.println(result.getLogInfo());
//			rdf = result.getContents();
		}
		catch (Throwable e) {
			String error = "Unexpected: error while reading from: " +full_path+ " : " +e.getMessage();
			log.info(error);
			registerOntologyResult.setError(error);
			return registerOntologyResult;
		}
		
		// ok, we have our ontology:
		
		
		//////////////////////////////////////////////////////////////////////////
		// finally, do actual registration to MMI registry

		// Get final URI of resulting model
		final String uri = createOntologyResult.getUri();
		assert uri != null;
		assert loginResult.getUserId() != null;
		assert loginResult.getSessionId() != null;
		
		log.info(": registering URI: " +uri+ " ...");

		CreateOntologyInfo createOntologyInfo = createOntologyResult.getCreateOntologyInfo();

		String ontologyId = createOntologyInfo.getPriorOntologyInfo().getOntologyId();
		String ontologyUserId = createOntologyInfo.getPriorOntologyInfo().getOntologyUserId();
		
		if ( ontologyId != null ) {
			log.info("Will create a new version for ontologyId = " +ontologyId+ ", userId=" +ontologyUserId);
		}
		
		
		Map<String, String> newValues = createOntologyInfo .getMetadataValues();
		

		try {
			// this is to get the filename for the registration
			String fileName = new URL(uri).getPath();
			
			//
			// make sure the fileName ends with ".owl" as the aquaportal back-end seems
			// to add that fixed extension in some operations (at least in the parse operation)
			//
			if ( ! fileName.toLowerCase().endsWith(".owl") ) {
				if ( log.isDebugEnabled() ) {
					log.debug("register: setting file extension to .owl per aquaportal requirement.");
				}
				fileName += ".owl";
			}
			
			
			if ( ontologyId == null ) {
				//
				// Submission of a new ontology (not version of a registered one)
				//
				
				// We are about to do the actual registration. But first, re-check that there is NO a preexisting
				// ontology that may conflict with this one.
				// NOTE: this check has been done already in the review operation; however, we repeat it here
				// in case there is a new registration done by other user in the meantime. Of course, we
				// are NOT completely solving the potential concurrency problem with this re-check; we are just
				// reducing the chances of that event.
				
				
				// TODO: the following can be simplified by obtaining the unversioned form of the URI and
				// calling Util2.checkNoPreexistingOntology(unversionedUri, registerOntologyResult)

				final String namespaceRoot = newValues.get("namespaceRoot") != null 
						? newValues.get("namespaceRoot")
						:  defaultNamespaceRoot;

				final String orgAbbreviation = createOntologyInfo.getAuthority();
				final String shortName = createOntologyInfo.getShortName();

				if ( ! Util2.checkNoPreexistingOntology(namespaceRoot, orgAbbreviation, shortName, registerOntologyResult) ) {
					return registerOntologyResult;
				}

			}
			else {
				// This is a submission of a *new version* of an existing ontology.
				// Nothing needs to be checked here.
				// NOTE: We don't need to repeat the _checkUriKeyCombinationForNewVersion step here
				// as any change in the contents of the metadata forces the user to explicitly
				// do the "review" operation, which already takes care of that check.
			}
			
			// OK, now do the actual registration:
			OntologyUploader createOnt = new OntologyUploader(uri, fileName, rdf, 
					loginResult,
					ontologyId, ontologyUserId,
					newValues
			);
			String res = createOnt.create();
			
			if ( res.startsWith("OK") ) {
				registerOntologyResult.setUri(uri);
				registerOntologyResult.setInfo(res);
				
				// issue #168 fix:
				// request that the ontology be loaded in the "ont" graph:
				OntServiceUtil.loadOntologyInGraph(uri, null);
			}
			else {
				registerOntologyResult.setError(res);
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
			registerOntologyResult.setError(ex.getClass().getName()+ ": " +ex.getMessage());
		}
		
		log.info("registerOntologyResult = " +registerOntologyResult);

		
		return registerOntologyResult;
	}


	// TODO
	public RegisterOntologyResult registerOntologyReHosted(CreateOntologyResult createOntologyResult, RegisterOntologyResult registerOntologyResult, LoginResult loginResult) {
		
		String full_path = createOntologyResult.getFullPath();
		
		log.info("registerOntology: Reading in temporary file: " +full_path);
		
		File file = new File(full_path);
		
		// Get resulting model:
		String rdf;
		try {
			rdf = Util2.readRdfWithCheckingUtf8(file);
			
			// conversion to UTF-8: the following code was not finally enabled
//			ReadFileResult result = Utf8Util.readFileWithConversionToUtf8(file);
//			if ( result.getError() != null ) {
//				String error = "Cannot read RDF model: " +full_path+ " : " +result.getError()+ "\n"
//					+ result.getLogInfo();
//				log.info(error);
//				registerOntologyResult.setError(error);
//				return registerOntologyResult;
//			}
//			System.out.println(result.getLogInfo());
//			rdf = result.getContents();
		}
		catch (Throwable e) {
			String error = "Unexpected: error while reading from: " +full_path+ " : " +e.getMessage();
			log.info(error);
			registerOntologyResult.setError(error);
			return registerOntologyResult;
		}
		
		// ok, we have our ontology:
		
		
		//////////////////////////////////////////////////////////////////////////
		// finally, do actual registration to MMI registry

		// Get final URI of resulting model
		final String uri = createOntologyResult.getUri();
		assert uri != null;
		assert loginResult.getUserId() != null;
		assert loginResult.getSessionId() != null;
		
		log.info(": registering ...");

		CreateOntologyInfo createOntologyInfo = createOntologyResult.getCreateOntologyInfo();

		String ontologyId = createOntologyInfo.getPriorOntologyInfo().getOntologyId();
		String ontologyUserId = createOntologyInfo.getPriorOntologyInfo().getOntologyUserId();
		
		if ( ontologyId != null ) {
			log.info("Will create a new version for ontologyId = " +ontologyId+ ", userId=" +ontologyUserId);
		}
		
		
		Map<String, String> newValues = createOntologyInfo .getMetadataValues();
		

		try {
			
			String fileName = new URL(uri).getPath();
			
			//
			// make sure the fileName ends with ".owl" as the aquaportal back-end seems
			// to add that fixed extension in some operations (at least in the parse operation)
			//
			if ( ! fileName.toLowerCase().endsWith(".owl") ) {
				log.info("register: setting file extension to .owl per aquaportal requirement.");
				fileName += ".owl";
			}
			
			
			// OK, now do the actual registration:
			OntologyUploader createOnt = new OntologyUploader(uri, fileName, rdf, 
					loginResult,
					ontologyId, ontologyUserId,
					newValues
			);
			String res = createOnt.create();
			
			if ( res.startsWith("OK") ) {
				registerOntologyResult.setUri(uri);
				registerOntologyResult.setInfo(res);
				
				// issue #168 fix:
				// request that the ontology be loaded in the "ont" graph:
				OntServiceUtil.loadOntologyInGraph(uri, null);
			}
			else {
				registerOntologyResult.setError(res);
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
			registerOntologyResult.setError(ex.getClass().getName()+ ": " +ex.getMessage());
		}
		
		log.info("registerOntologyResult = " +registerOntologyResult);

		
		return registerOntologyResult;
	}

	
	public RegisterOntologyResult registerOntology_oldMethod(CreateOntologyResult createOntologyResult, LoginResult loginResult) {
		RegisterOntologyResult registerOntologyResult = new RegisterOntologyResult();
		
		
		String full_path = createOntologyResult.getFullPath();
		
		log.info("registerOntology: Reading in temporary file: " +full_path);
		
		File file = new File(full_path);
		if ( ! file.canRead() ) {
			String error = "Unexpected: cannot read: " +full_path;
			log.info(error);
			registerOntologyResult.setError(error);
			return registerOntologyResult;
		}
		
		// Get resulting model:
		String rdf;
		try {
//			rdf = Util2.readRdf(file);
			rdf = Util2.readRdfWithCheckingUtf8(file);
			
			// conversion to UTF-8: the following code was not finally enabled
//			ReadFileResult result = Utf8Util.readFileWithConversionToUtf8(file);
//			if ( result.getError() != null ) {
//				String error = "Cannot read RDF model: " +full_path+ " : " +result.getError()+ "\n"
//					+ result.getLogInfo();
//				log.info(error);
//				registerOntologyResult.setError(error);
//				return registerOntologyResult;
//			}
//			System.out.println(result.getLogInfo());
//			rdf = result.getContents();
		}
		catch (Throwable e) {
			String error = "Unexpected: error while reading from: " +full_path+ " : " +e.getMessage();
			log.info(error);
			registerOntologyResult.setError(error);
			return registerOntologyResult;
		}
		
		// ok, we have our ontology:
		
		
		//////////////////////////////////////////////////////////////////////////
		// finally, do actual registration to MMI registry

		// Get final URI of resulting model
		// FIXME this uses the same original URI
		final String uri = createOntologyResult.getUri();
		assert uri != null;
		assert loginResult.getUserId() != null;
		assert loginResult.getSessionId() != null;
		
		log.info(": registering ...");

		CreateOntologyInfo createOntologyInfo = createOntologyResult.getCreateOntologyInfo();

		String ontologyId = createOntologyInfo.getPriorOntologyInfo().getOntologyId();
		String ontologyUserId = createOntologyInfo.getPriorOntologyInfo().getOntologyUserId();
		
		if ( ontologyId != null ) {
			log.info("Will create a new version for ontologyId = " +ontologyId+ ", userId=" +ontologyUserId);
		}
		
		
		Map<String, String> newValues = createOntologyInfo .getMetadataValues();
		

		try {
			
			String fileName = new URL(uri).getPath();
			
			//
			// make sure the fileName ends with ".owl" as the aquaportal back-end seems
			// to add that fixed extension in some operations (at least in the parse operation)
			//
			if ( ! fileName.toLowerCase().endsWith(".owl") ) {
				log.info("register: setting file extension to .owl per aquaportal requirement.");
				fileName += ".owl";
			}
			
			
			if ( ! createOntologyResult.isPreserveOriginalBaseNamespace() ) {
				// We are about to do the actual registration. But first, re-check that there is NO a preexisting
				// ontology that may conflict with this one.
				// NOTE: this check has been done already in the review operation; however, we repeat it here
				// in case there is a new registration done by other user in the meantime. Of course, we
				// are NOT completely solving the potential concurrency problem with this re-check; we are just
				// reducing the chances of that event.
				if ( ontologyId == null ) {
					
					final String namespaceRoot = newValues.get("namespaceRoot") != null 
							? newValues.get("namespaceRoot")
							:  defaultNamespaceRoot;
	
					final String orgAbbreviation = newValues.get(OmvMmi.origMaintainerCode.getURI());
					final String shortName = newValues.get(Omv.acronym.getURI());
	
					if ( ! Util2.checkNoPreexistingOntology(namespaceRoot, orgAbbreviation, shortName, registerOntologyResult) ) {
						return registerOntologyResult;
					}
	
				}
				else {
					// This is a submission of a *new version* of an existing ontology.
					// Nothing needs to be checked here.
					// NOTE: We don't need to repeat the _checkUriKeyCombinationForNewVersion step here
					// as any change in the contents of the metadata forces the user to explicitly
					// do the "review" operation, which already takes care of that check.
				}
			}
			
			// OK, now do the actual registration:
			OntologyUploader createOnt = new OntologyUploader(uri, fileName, rdf, 
					loginResult,
					ontologyId, ontologyUserId,
					newValues
			);
			String res = createOnt.create();
			
			if ( res.startsWith("OK") ) {
				registerOntologyResult.setUri(uri);
				registerOntologyResult.setInfo(res);
				
				// issue #168 fix:
				// request that the ontology be loaded in the "ont" graph:
				OntServiceUtil.loadOntologyInGraph(uri, null);
			}
			else {
				registerOntologyResult.setError(res);
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
			registerOntologyResult.setError(ex.getClass().getName()+ ": " +ex.getMessage());
		}
		
		log.info("registerOntologyResult = " +registerOntologyResult);

		
		return registerOntologyResult;
	}

	
	
	
	public TempOntologyInfo getTempOntologyInfo(String uploadResults, boolean includeContents,
			boolean includeRdf
	) {
		TempOntologyInfo tempOntologyInfo = new TempOntologyInfo();
		
		if ( metadataBaseInfo == null ) {
			tempOntologyInfo.setError("IServer not properly initialized!");
			return tempOntologyInfo;
		}
		
		TempOntologyHelper tempOntologyHelper = new TempOntologyHelper(metadataBaseInfo);
		tempOntologyHelper.getTempOntologyInfo(uploadResults, tempOntologyInfo, includeRdf);
		
		if ( tempOntologyInfo.getError() != null ) {
			return tempOntologyInfo;
		}
		
		if ( includeContents ) {
			_getOntologyContents(tempOntologyInfo);
		}
		
		return tempOntologyInfo;
	}
	
	/** similar to {@link #getOntologyContents(RegisteredOntologyInfo)} but reading the model
	 * from the internal path.
	 */
	private TempOntologyInfo _getOntologyContents(TempOntologyInfo tempOntologyInfo) {
		
		if ( log.isDebugEnabled() ) {
			log.debug("_getOntologyContents(TempOntologyInfo): loading model");
		}
		
		String full_path = tempOntologyInfo.getFullPath();
		File file = new File(full_path );
		String uriFile = file.toURI().toString();
		log.info("Loading model: " +uriFile);

		OntModel ontModel;
		try {
			ontModel = JenaUtil.loadModel(uriFile, false);
		}
		catch (Throwable ex) {
			String error = "Unexpected error: " +ex.getClass().getName()+ " : " +ex.getMessage();
			log.info(error);
			tempOntologyInfo.setError(error);
			return tempOntologyInfo;
		}


		// Metadata:
		if ( log.isDebugEnabled() ) {
			log.debug("_getOntologyContents(TempOntologyInfo): getting metadata");
		}
		MetadataExtractor.prepareOntologyMetadata(metadataBaseInfo, ontModel, tempOntologyInfo);

		
		// Data
		if ( log.isDebugEnabled() ) {
			log.debug("_getOntologyContents(TempOntologyInfo): getting entities");
		}
		
		try {
			QueryUtil.getEntities(tempOntologyInfo, ontModel);
		}
		catch (Exception e) {
			String error = "Error loading model: " +e.getMessage();
			log.error(error, e);
			tempOntologyInfo.setError(error);
			return tempOntologyInfo;
		}
		
		
		return tempOntologyInfo;
	
	}

	
	////////////////////////////////////////////////////////////////////////////////////////////
	// VINE:
	
	public List<RelationInfo> getVineRelationInfos() {
		if ( log.isDebugEnabled() ) {
			log.debug("getRelationInfos starting");
		}
	
		// TODO: determine mechanism to obtain the list of default mapping relations, for example,
		// from an ontology.
		
		// For now, creating a hard-coded list
		
		List<RelationInfo> relInfos = new ArrayList<RelationInfo>();
		
//      URI="http://www.w3.org/2008/05/skos#exactMatch"
//      icon="icons/exactMatch28.png"
//      name="exactMatch"
//      tooltip="The property skos:exactMatch is used to link two concepts, indicating a high degree of confidence that the concepts can be used interchangeably across a wide range of information retrieval applications. [SKOS Section 10.1] (transitive, symmetric)"
		relInfos.add(new RelationInfo(
				"http://www.w3.org/2008/05/skos#exactMatch", 
				"exactMatch28.png", 
				"exactMatch",
				"The property skos:exactMatch is used to link two concepts, indicating a high degree of confidence that the concepts can be used interchangeably across a wide range of information retrieval applications. [SKOS Section 10.1] (transitive, symmetric)"
		));
		
//      URI="http://www.w3.org/2008/05/skos#closeMatch"
//      icon="icons/closeMatch28.png"
//      name="closeMatch"
//      tooltip="A skos:closeMatch link indicates that two concepts are sufficiently similar that they can be used interchangeably in some information retrieval applications. [SKOS Section 10.1] (symmetric)"
		relInfos.add(new RelationInfo(
				"http://www.w3.org/2008/05/skos#closeMatch", 
				"closeMatch28.png", 
				"closeMatch",
				"A skos:closeMatch link indicates that two concepts are sufficiently similar that they can be used interchangeably in some information retrieval applications. [SKOS Section 10.1] (symmetric)"
		));
		
//      URI="http://www.w3.org/2008/05/skos#broadMatch"
//      icon="icons/broadMatch28.png"
//      name="broadMatch"
//      tooltip="'has the broader concept': the second (object) concept is broader than the first (subject) concept [SKOS Section 8.1] (infers broaderTransitive, a transitive relation)"
		relInfos.add(new RelationInfo(
				"http://www.w3.org/2008/05/skos#broadMatch", 
				"broadMatch28.png", 
				"broadMatch",
				"'has the broader concept': the second (object) concept is broader than the first (subject) concept [SKOS Section 8.1] (infers broaderTransitive, a transitive relation)"
		));

//      URI="http://www.w3.org/2008/05/skos#narrowMatch"
//      icon="icons/narrowMatch28.png"
//      name="narrowMatch"
//      tooltip="'has the narrower concept': the second (object) concept is narrower than the first (subject) concept [SKOS Section 8.1] (infers narrowTransitive, a transitive relation)"
		relInfos.add(new RelationInfo(
				"http://www.w3.org/2008/05/skos#narrowMatch", 
				"narrowMatch28.png", 
				"narrowMatch",
				"'has the narrower concept': the second (object) concept is narrower than the first (subject) concept [SKOS Section 8.1] (infers narrowTransitive, a transitive relation)"
		));

//      URI="http://www.w3.org/2008/05/skos#relatedMatch"
//      icon="icons/relatedMatch28.png"
//      name="relatedMatch"
//      tooltip="The property skos:relatedMatch is used to state an associative mapping link between two concepts. [SKOS Section 8.1] (symmetric)"
		relInfos.add(new RelationInfo(
				"http://www.w3.org/2008/05/skos#relatedMatch", 
				"relatedMatch28.png", 
				"relatedMatch",
				"The property skos:relatedMatch is used to state an associative mapping link between two concepts. [SKOS Section 8.1] (symmetric)"
		));

		if ( log.isDebugEnabled() ) {
			log.debug("getRelationInfos returning: " +relInfos);
		}

		return relInfos;

	}
	
	
	////////////////////////////////////////////////////////////////////////////////////////////
	// Search:
	
	public SparqlQueryResult runSparqlQuery(SparqlQueryInfo query) {
		SparqlQueryResult sparqlQueryResult = new SparqlQueryResult();
		try {
			String result;
			if ( query.getAcceptEntries().length == 0 ) {
				// this is the call used for previous version of the SparqlQueryInfo object, which didn't
				// have the acceptEntries attribute
				result = OntServiceUtil.runSparqlQuery(query.getQuery(), query.getFormat(), "application/rdf+xml");
			}
			else {
				// use the new call:
				result = OntServiceUtil.runSparqlQuery(query);
			}
			sparqlQueryResult.setResult(result);
		}
		catch (Exception e) {
			String error = "Error while dispatching query: " +e.getMessage();
			sparqlQueryResult.setError(error);
		}
		return sparqlQueryResult;
	}

	
	// login:
	
	
	public LoginResult authenticateUser(String userName, String userPassword) {
		LoginResult loginResult = new LoginResult();
		
		log.info(": authenticating user " +userName+ " ...");
		try {
			UserAuthenticator login = new UserAuthenticator(userName, userPassword);
			login.getSession(loginResult);
		}
		catch (Exception ex) {
			loginResult.setError(ex.getMessage());
		}

		return loginResult;
		
	}
	
	public ResetPasswordResult resetUserPassword(String username) {
		ResetPasswordResult result = new ResetPasswordResult();
		
		// Issue 258:"reset password in effect but lost if error while sending email"
		// Part of the fix is to first check the mail account parameters are given:
		final String mail_user = ServerConfig.Prop.MAIL_USER.getValue();
		final String mail_password = ServerConfig.Prop.MAIL_PASSWORD.getValue();
		if ( mail_user == null || mail_user.equals("-") ) {
			String error = "Email server account not configured. Please report this bug. (u)";
			result.setError(error);
			log.error(error);
			return result;
		}
		if ( mail_password == null  || mail_password.equals("-") ) {
			String error = "Email server account not configured. Please report this bug. (p)";
			result.setError(error);
			log.error(error);
			return result;
		}

		///////////////////////////////////////////////////////////////
		// Get email address for the user
		UserInfoResult userInfoResult = getUserInfo(username);
		if ( userInfoResult.getError() != null ) {
			result.setError(userInfoResult.getError());
			return result;
		}
		final String email = userInfoResult.getProps().get("email");
		if ( email == null ) {
			result.setError("No email associated with username: " +username);
			return result;
		}
		
		///////////////////////////////////////////////////////////////
		// get new password
		String newPassword = Util2.generatePassword();
		
		///////////////////////////////////////////////////////////////
		// update password in back-end
		Map<String, String> values = userInfoResult.getProps();
		values.put("id", userInfoResult.getProps().get("id"));
		values.put("password", newPassword);
		values.put("sessionid", "4444444444444");
		CreateUpdateUserAccountResult updatePwResult = createUpdateUserAccount(values);
		if ( updatePwResult.getError() != null ) {
			result.setError(updatePwResult.getError());
			return result;
		}
		
		
		///////////////////////////////////////////////////////////////
		// send email with new password
		
		boolean debug = false;
		final String from = "MMI-ORR <techlead@marinemetadata.org>";
		final String replyTo = "techlead@marinemetadata.org";
		final String subject = "Password reset";
		final String text = "Your MMI ORR password has been reset.\n" +
				"\n" +
				"Username: " +username+ "\n" +
				"   email: " +email+ "\n" +
				"Password: " +newPassword+ "\n" +
				"\n"
		;
		
		try {
			MailSender.sendMessage(mail_user, mail_password, debug , from, email, replyTo, subject, text);
			result.setEmail(email);
		}
		catch (Exception e) {
			String error = "Error sending email: " +e.getMessage();
			result.setError(error);
			log.error(error, e);
		}
		
		return result;
	}

	
	public UserInfoResult getUserInfo(String username) {
		UserInfoResult result = new UserInfoResult();
		
		try {
			Map<String, String> props = OntServiceUtil.getUserInfo(username);
			result.setProps(props);
		}
		catch (Exception e) {
			String error = "error getting user information: " +e.getMessage();
			result.setError(error);
			log.error(error, e);
		}
		
		return result;
	}
	
	
	public CreateUpdateUserAccountResult createUpdateUserAccount(Map<String,String> values) {
		CreateUpdateUserAccountResult result = new CreateUpdateUserAccountResult();
		
		String email = values.get("email");
		if ( email != null ) {
			try {
				new InternetAddress(email, true);
			}
			catch (AddressException e) {
				String error = "Malformed email address: " +e.getMessage();
				result.setError(error);
				return result;
			}
		}
		
		try {
			UserAccountCreatorUpdater uacu = new UserAccountCreatorUpdater(values);
			uacu.doIt(result);
		}
		catch (Exception e) {
			String error = "error updating user information: " +
				e.getClass().getName()+ " : " +e.getMessage();
			result.setError(error);
			log.error(error, e);
		}
		
		return result;
		
	}
	
	
	
	/////////////////////////////////////////////////////////////////////////////////////////////////
	// OOI CI semantic prototype
	public RegisterOntologyResult registerOntologyDirectly(
			LoginResult loginResult, 
			RegisteredOntologyInfo registeredOntologyInfo,
			CreateOntologyInfo createOntologyInfo, 
			String graphId
	) {
		return OoiCi.registerOntologyDirectly(loginResult, registeredOntologyInfo, createOntologyInfo, graphId);
	}

	
	public InternalOntologyResult prepareUsersOntology(LoginResult loginResult) {
		InternalOntologyResult result = new InternalOntologyResult();
		
		try {
			InternalManager.prepareUsersOntology(this, loginResult, result);
		}
		catch (Exception e) {
			String error = e.getMessage();
			result.setError(error);
			log.error(error, e);
		}
		
		return result;
	}

	public InternalOntologyResult createGroupsOntology(LoginResult loginResult) {
		InternalOntologyResult result = new InternalOntologyResult();
		
		try {
			InternalManager.createGroupsOntology(this, loginResult, result);
		}
		catch (Exception e) {
			String error = e.getMessage();
			result.setError(error);
			log.error(error, e);
		}
		
		return result;
	}
	
	
	public UnregisterOntologyResult unregisterOntology(LoginResult loginResult, RegisteredOntologyInfo oi) {
		UnregisterOntologyResult result = new UnregisterOntologyResult();
		
		log.debug("unregisterOntology called.");
		
		if ( loginResult == null || ! loginResult.isAdministrator() ) {
			String error = "Unregister ontology: Only an administrator can perform this operation.";
			log.debug(error);
			result.setError(error);
			return result;
		}

		String ontUri = oi.getUri();
		String version = oi.getVersionNumber();

		result.setUri(ontUri);
		result.setVersionNumber(version);

		String error = null;
		Throwable thr = null;
		
		try {
			if ( ! OntServiceUtil.unregisterOntology(ontUri, version) ) {
				error = "Unregister ontology: Ont service could not perform the removal. " +
						"Please try again later.\n\n" +
						"If the problem persists, please notify the developers.";
			}
		}
		catch (Exception e) {
			error = e.getMessage();
			thr = e;
		}
		
		if ( error != null ) {
			log.debug(error, thr);
			result.setError(error);
			return result;
		}
		else {
			result.setInfo("Unregistration completed.");
		}
		
		return result;
		
	}
}
