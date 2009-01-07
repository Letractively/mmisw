package org.mmisw.ont;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * A helper to work with the database.
 * 
 * @author Carlos Rueda
 */
public class Db {
	
	private final Log log = LogFactory.getLog(Db.class);
	
	private final OntConfig ontConfig;

	// obtained from the config in the init() method
	private String aquaportalDatasource; 
	
	// obtained in the init() methos
	private DataSource dataSource;
	

	/** 
	 * Creates an instance of this helper. 
	 * Call {@link #init()} to initialize it.
	 * @param ontConfig Used at initialization.
	 */
	Db(OntConfig ontConfig) {
		this.ontConfig = ontConfig;
	}
	
	/** 
	 * Required initialization.
	 */
	void init() throws Exception {
		log.debug("init called.");
		aquaportalDatasource = ontConfig.getProperty(OntConfig.Prop.AQUAPORTAL_DATASOURCE); 
		
		try {
			Context initialContext = new InitialContext();
			dataSource = (DataSource) initialContext.lookup(aquaportalDatasource);
			if ( dataSource == null ) {
				throw new ServletException("Failed to lookup datasource.");
			}
		}
		catch ( NamingException ex ) {
			throw new ServletException("Failed to lookup datasource.", ex);
		}

	}
	
	public Connection getConnection() throws SQLException, ServletException {
		Connection result = dataSource.getConnection();
		return result;
    }
	
	
	/**
	 * Obtains the ontology by the given URI.
	 * 
	 * This uses the ontologyUri given. To try other file extensions,
	 * use {@link #getOntologyWithExts(MmiUri, String[])}.
	 * 
	 * @param ontologyUri The ontology URI as exactly stored in the database.
	 * @return
	 * @throws ServletException
	 * @throws SQLException 
	 */
	private Ontology getOntology(String ontologyUri) throws ServletException {
		Connection _con = null;
		try {
			_con = getConnection();
			Statement _stmt = _con.createStatement();

			if ( true ) {
				String query = 
					"select v.id, v.ontology_id, v.file_path " +
					"from v_ncbo_ontology v " +
					"where v.urn='" +ontologyUri+ "'";
				
				ResultSet rs = _stmt.executeQuery(query);
				
		        if ( rs.next() ) {
		        	Ontology ontology = new Ontology();
		        	ontology.id = rs.getString(1);
		        	ontology.ontology_id = rs.getString(2);
		        	ontology.file_path = rs.getString(3);
		        	
		        	try {
		        		URL uri_ = new URL(ontologyUri);
		        		ontology.filename = new File(uri_.getPath()).getName();
		        	}
		        	catch (MalformedURLException e) {
		        		// should not occur.
		        		log.debug("should not occur.", e);
		        	}
		        	return ontology;
		        }
			}
			else{
				String query = 
					"select v.id, v.ontology_id, v.file_path, f.filename " +
					"from v_ncbo_ontology v, ncbo_ontology_file f " +
					"where v.urn='" +ontologyUri+ "'" +
					"  and v.id = f.ontology_version_id";
				
				ResultSet rs = _stmt.executeQuery(query);
				
		        if ( rs.next() ) {
		        	Ontology ontology = new Ontology();
		        	ontology.id = rs.getString(1);
		        	ontology.ontology_id = rs.getString(2);
		        	ontology.file_path = rs.getString(3);
		        	ontology.filename = rs.getString(4);
		        	return ontology;
		        }
			}
		}
		catch (SQLException e) {
			throw new ServletException(e);
		}
		finally {
			if ( _con != null ) {
				try {
					_con.close();
				}
				catch (SQLException e) {
					log.warn("Error closing connection", e);
				}
			}
		}
		
		return null;
	}
	
	
	/**
	 * Gets an ontology by trying the original ontology URI, and then, if that fails,
	 * with the file extensions "", ".owl", and ".rdf" in sequence until successful
	 * or returning null if none of these tries works.
	 * 
	 * @param mmiUri
	 * @param foundUri If not null, the URI that was success is stored at foundUri[0]. 
	 * 
	 * @throws ServletException 
	 */
	Ontology getOntologyWithExts(MmiUri mmiUri, String[] foundUri) throws ServletException {
		// try with given URI:
		String ontologyUri = mmiUri.getOntologyUri();
		Ontology ontology = this.getOntology(ontologyUri);
		if ( ontology != null ) {
			if ( foundUri != null ) {
				foundUri[0] = ontologyUri;
			}
			return ontology;
		}
		
		// try with a different extension, including no extension:
		String[] exts = { "", ".owl", ".rdf" };
		String topicExt = mmiUri.getTopicExtension();
		for (String ext : exts ) {
			if ( ! ext.equalsIgnoreCase(topicExt) ) {
				String withNewExt = mmiUri.getOntologyUriWithTopicExtension(ext);
				ontology = this.getOntology(withNewExt);
				if ( ontology != null ) {
					if ( foundUri != null ) {
						foundUri[0] = withNewExt;
					}
					return ontology;
				}
			}
		}

		return ontology;
	}	
	

	/**
	 * Gets the list of versions of a given ontology according to the corresponding
	 * mmiUri identification, which is used to create the query:
	 * <ul>
	 *    <ul> Use wildcard "%" for the version 
	 *    
	 *    <ul> If allExts is true, search for topic with NO extension, but also allow
	 *       the same topic with any extension ".%" (Note: the dot is important to
	 *       avoid getting topics that have the topic in question as suffix).
	 * </ul>
	 * 
	 * <p>
	 * Note that the term component in the given URI is ignored.
	 * 
	 * <p>
	 * The elements are sorted such that the first element is the most recent version
	 * available.
	 * 
	 * @param mmiUri the base URI to create the version wilcard.
	 * @param  allExts  false to use the topic as given (with its extension);
	 *                  true to ignore the extension and use a wildcard.
	 * 
	 * @return list of ontologies with exactly the same given mmiUri except for the
	 *          version component.
	 *          
	 * @throws ServletException
	 */
	List<Ontology> getOntologyVersions(MmiUri mmiUri, boolean allExts) throws ServletException {
		
		// ignore the term component, if given in the mmiUri
		String term = mmiUri.getTerm();
		if ( term != null && term.trim().length() > 0 ) {
			mmiUri = mmiUri.copyWithTerm("");
			log.debug("getOntologyVersions: " +term+ ": term ignored.");
		}
		
		List<Ontology> onts = new ArrayList<Ontology>();
		
		if ( allExts ) {
			// remove original topic extension, if any:
			if ( mmiUri.getTopicExtension().length() > 0 ) {
				String uriNoExt = mmiUri.getOntologyUriWithTopicExtension("");
				try {
					mmiUri = MmiUri.create(uriNoExt);
				}
				catch (URISyntaxException e) {
					// should not occur.
					log.debug("should not occur.", e);
					return onts;
				}
			}
		}
		
		String origVersion = mmiUri.getVersion();
		if ( origVersion != null && log.isDebugEnabled() ) {
			log.debug("getOntologyVersions: " +origVersion+ ": version component will be ignored.");
		}
		
		// get ontologyUriPattern to do the "like" query:
		String ontologyUriPattern = "";
		try {
			// use "%" for the version:
			MmiUri mmiUriPatt = mmiUri.copyWithVersionNoCheck("%");
			ontologyUriPattern = mmiUriPatt.getOntologyUri();
		}
		catch (URISyntaxException e) {
			// should not occur.
			log.debug("should not occur.", e);
			return onts;
		}
		
		// to be added to the condition is the query string below:
		String or_with_dot_ext = "";
		
		if ( allExts ) {
			// Add an "or" condition to allow extensions, ".%":
			or_with_dot_ext = "or v.urn like '" +ontologyUriPattern+ ".%' ";
		}
		
		// ok, now run the "like" query
		Connection _con = null;
		try {
			_con = getConnection();
			Statement _stmt = _con.createStatement();

			String query = 
				"select v.id, v.ontology_id, v.file_path, v.urn " +
				"from v_ncbo_ontology v " +
				"where v.urn like '" +ontologyUriPattern+ "' " +
				or_with_dot_ext +
				"order by v.urn desc";

			if ( log.isDebugEnabled() ) {
				log.debug("Versions query: " +query);
			}
			
			ResultSet rs = _stmt.executeQuery(query);

			while ( rs.next() ) {
				Ontology ontology = new Ontology();
				ontology.id = rs.getString(1);
				ontology.ontology_id = rs.getString(2);
				ontology.file_path = rs.getString(3);
				
				String ontologyUri = rs.getString(4);
				ontology.setUri(ontologyUri);

				try {
					URL uri_ = new URL(ontologyUri);
					ontology.filename = new File(uri_.getPath()).getName();
					onts.add(ontology);
				}
				catch (MalformedURLException e) {
					// should not occur.
					log.debug("should not occur.", e);
				}
			}
		}
		catch (SQLException e) {
			throw new ServletException(e);
		}
		finally {
			if ( _con != null ) {
				try {
					_con.close();
				}
				catch (SQLException e) {
					log.warn("Error closing connection", e);
				}
			}
		}
		
		return onts;
	}
	
	
	
	Ontology getMostRecentOntologyVersion(MmiUri mmiUri) throws ServletException {
		List<Ontology> onts = getOntologyVersions(mmiUri, true);
		if ( onts.size() == 0 ) {
			return null;
		}
		Ontology ont = onts.get(0);
		return ont;
	}

	
	List<Ontology> getOntologies() throws ServletException {
		List<Ontology> onts = new ArrayList<Ontology>();
		Connection _con = null;
		try {
			_con = getConnection();
			Statement _stmt = _con.createStatement();

			// NOTE:
			//    v_ncbo_ontology.id  ==  ncbo_ontology_file.ontology_version_id
			//
			String query = 
				"select v.id, v.ontology_id, v.file_path, f.filename " +
				"from v_ncbo_ontology v, ncbo_ontology_file f " +
				"where v.id = f.ontology_version_id";
			
			ResultSet rs = _stmt.executeQuery(query);
			
	        while ( rs.next() ) {
	        	Ontology ontology = new Ontology();
	        	ontology.id = rs.getString(1);
	        	ontology.ontology_id = rs.getString(2);
	        	ontology.file_path = rs.getString(3);
	        	ontology.filename = rs.getString(4);
	        	onts.add(ontology);
	        }
		}
		catch (SQLException e) {
			throw new ServletException(e);
		}
		finally {
			if ( _con != null ) {
				try {
					_con.close();
				}
				catch (SQLException e) {
					log.warn("Error closing connection", e);
				}
			}
		}
	
		return onts;
	}
}