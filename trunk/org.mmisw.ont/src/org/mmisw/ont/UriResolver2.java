package org.mmisw.ont;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mmisw.ont.OntServlet.Request;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.Model;

import edu.drexel.util.rdf.JenaUtil;

/**
 * This is main dispatcher used by the entry point OntServlet.
 * 
 * <p>
 * Note: under development; this class is going to be a refactored version of UriResolver, which
 * will eventually disappear.
 * The strategy is to incrementally provide the central operations through this UriResolver2 class.
 * Some stuff will be moved out from UriResolver to OntServlet and some stuff refactored into
 * UriResolver2.
 * 
 * @author Carlos Rueda
 */
public class UriResolver2 {
	
	private final Log log = LogFactory.getLog(UriResolver2.class);
	
	private final OntConfig ontConfig;
	private final Db db;

	
	private Request req;
	
	
	public UriResolver2(OntConfig ontConfig, Db db, OntGraph ontGraph) {
		this.ontConfig = ontConfig;
		this.db = db;
	}
	
	
	/**
	 * Represents a response to be sent to the client
	 */
	abstract class Response {
		
		abstract void dispatch() throws IOException ;
	}
	
	class NotFoundResponse extends Response {
		final String res;
		
		public NotFoundResponse(String res) {
			this.res = res;
		}
		void dispatch() throws IOException {
			req.response.sendError(HttpServletResponse.SC_NOT_FOUND, res);
		}
	}
	
	class InternalErrorResponse extends Response {
		final String msg;
		final Throwable thr;
		
		public InternalErrorResponse(String msg) {
			this(msg, null);
		}
		public InternalErrorResponse(String msg, Throwable thr) {
			this.msg = msg;
			this.thr = thr;
		}
		void dispatch() throws IOException {
			log.error(msg, thr);
			req.response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg);
		}
	}
	

	class ModelResponse extends Response {
		final Model model;
		
		ModelResponse(Model model) {
			this.model = model;
		}
		
		void dispatch() throws IOException {
			ServletOutputStream os = req.response.getOutputStream();

			StringReader is = null;

			if ( req.outFormat.equalsIgnoreCase("owl")
			||   req.outFormat.equalsIgnoreCase("rdf")
			) {
				req.response.setContentType("Application/rdf+xml");
				is = OntServlet.serializeModel(model, "RDF/XML-ABBREV");
			}
			else if ( req.outFormat.equalsIgnoreCase("n3") ) {
				req.response.setContentType("text/plain");
				is = OntServlet.serializeModel(model, "N3");
			}
			else if ( req.outFormat.equalsIgnoreCase("json") ) {
//				req.response.setContentType("application/json");
				req.response.setContentType("text/plain");
				is = OntServlet.serializeModel(model, "N3");
			}
			else {
				req.response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
						"ModelResponse: outFormat " +req.outFormat+ " not recognized"
				);
				return;
			}
			
			IOUtils.copy(is, os);
		}
		
	}

	class TermResponse extends ModelResponse {
		TermResponse(Model termModel) {
			super(termModel);
		}
	}
	
	class OntologyResponse extends ModelResponse {
		OntologyResponse(Model ontologyModel) {
			super(ontologyModel);
		}
	}
	
	
	
	/**
	 * The main dispatcher.
	 */
	void service(Request req) throws ServletException, IOException {
		this.req = req;
		
		Response resp = null;
		
		final String fullRequestedUri = req.request.getRequestURL().toString();
		
		// parse the given URI:
		try {
			MmiUri mmiUri = new MmiUri(fullRequestedUri);
			// we have an MmiUri request.

			// get output format to be used:
			String outFormat = OntServlet.getOutFormatForMmiUri(req, mmiUri, log);
			
			resp = _getResponseForMmiUri(mmiUri, outFormat);
			
			return;
		}
		catch (URISyntaxException e) {
			// NOT a regular MmiUri request.
			String outFormat = OntServlet.getOutFormatForNonMmiUri(req, log); 
			resp = _getResponseForNonMmiUri(outFormat);
		}
		
		if ( resp != null ) {
			resp.dispatch();
		}
	}
	
	
	/**
	 * Gets the response for a requested MmiUri.
	 */
	private Response _getResponseForMmiUri(final MmiUri mmiUri, final String outFormat) throws ServletException, IOException {
		
		// the ontology we need to access:
		Ontology ontology = null;
		
		
		// this flag will be true if we have an unversioned request, see Issue 24.
		boolean unversionedRequest = false;
		MmiUri foundMmiUri = null;
		
		String version = mmiUri.getVersion();
		if ( version == null || version.equals(MmiUri.LATEST_VERSION_INDICATOR) ) {
			
			//
			// handling of unversioned and latest-version requests.  (see Issue 24)
			//
			
			// Get latest version trying all possible topic extensions:
			Ontology mostRecentOntology = db.getMostRecentOntologyVersion(mmiUri);

			if ( mostRecentOntology != null ) {
				
				try {
					//
					// Note that mostRecentOntology.getUri() won't have the term component.
					// So, we have to transfer it to foundMmiUri:
					//
					foundMmiUri = new MmiUri(mostRecentOntology.getUri()).copyWithTerm(mmiUri.getTerm());
					
					if ( log.isDebugEnabled() ) {
						log.debug("Found ontology version: " +mostRecentOntology.getUri());

						if ( ! mmiUri.getExtension().equals(foundMmiUri.getExtension()) ) {
							log.debug("Restored requested extension to: " +mmiUri);
						}
					}
					
					// also, restore the original requested extension:
					foundMmiUri = foundMmiUri.copyWithExtension(mmiUri.getExtension());
				}
				catch (URISyntaxException e) {
					return new InternalErrorResponse("Shouldn't happen", e);
				}
				
				// OK: here, foundMmiUri refers to the latest version.
				
				if ( version == null ) {
					// unversioned request.
					unversionedRequest = true;
					// and let the dispatch continue.
				}
				else {
					// request was with version = MmiUri.LATEST_VERSION_INDICATOR.
					// Use a redirect so the user gets the actual latest version:
					//
					final String latestUri = foundMmiUri.getTermUri();
					return new Response() {
						void dispatch() throws IOException {
							if ( log.isDebugEnabled() ) {
								log.debug("Redirecting to latest version: " + latestUri);
							}
							String redir_latestUri = req.response.encodeRedirectURL(latestUri);
							req.response.sendRedirect(redir_latestUri);
						}
					};
				}
			}
			else {
				// No versions available!
				if ( log.isDebugEnabled() ) {
					log.debug("No versions found.");
				}
				return new NotFoundResponse(mmiUri.getTermUri());
			}
		}
		else {
			// Version explicitly given: let the dispatch continue.
		}
		

		if ( ontology == null ) {
			// obtain info about the ontology:
			ontology = db.getOntologyWithExts(mmiUri, null);
		}
		
		if ( ontology == null ) {
			if ( log.isDebugEnabled() ) {
				log.debug("_getResponseForMmiUri: not dispatched here. mmiUri = " +mmiUri);
			}
			return new NotFoundResponse(mmiUri.getTermUri());
		}
		

		
		final File file = OntServlet.getFullPath(ontology, ontConfig, log);
		
		
		if ( !file.canRead() ) {
			// This should not happen.
			return new InternalErrorResponse(
					file.getAbsolutePath()+ ": internal error: uploaded file "
					+ (file.exists() ? "exists but cannot be read." : "not found.")
					+ "Please, report this bug."
			);
		}

		// original model:
		final String uriFile = file.toURI().toString();
		final OntModel originalModel = JenaUtil.loadModel(uriFile, false);

		if ( originalModel == null ) {
			// This should not happen.
			return new InternalErrorResponse(
					file.getAbsolutePath()+ ": internal error: uploaded file "
					+ "cannot be read as an ontology model. "
					+ "Please, report this bug."
			);
		}

		// corresponding unversioned model in case is requested: 
		OntModel unversionedModel = null;
		
		if ( unversionedRequest ) {

			unversionedModel = UnversionedConverter.getUnversionedModel(originalModel, mmiUri);
			
			if ( unversionedModel != null ) {
				if ( log.isDebugEnabled() ) {
					log.debug("_getResponseForMmiUri: obtained unversioned model");
				}
			}
			else {
				// error in conversion to unversioned version.
				// this is unexpected. 
				// Continue with original model, if necessary; see below.
				log.error("_getResponseForMmiUri: unexpected: error in conversion to unversioned version.  But continuing with original model");
			}
		}

		String term = mmiUri.getTerm();
		
		if ( log.isDebugEnabled() ) {
			log.debug("_getResponseForMmiUri: term=[" +term+ "].");
		}

		
		OntModel model = unversionedModel != null ? unversionedModel : originalModel;

		/////////////////////////////////////////////////////////////////////
		// Term included?
		if ( term.length() > 0 ) {

			Model termModel = TermExtractor.getTermModel(model, mmiUri);
		
			if ( termModel != null ) {
				return new TermResponse(termModel);
			}
			
		}
		
		// Else: term unexistent: return 404 to client:
		return new NotFoundResponse(mmiUri.getTermUri());

	}

	
	
	private Response _getResponseForNonMmiUri(String outFormat) {
		// ...   
		// NOT HANDLED YET -- I'm still testing the main dispatch above so I should be
		// only making regular MmiUri request for the moment. (see "ur2" in OntServlet)
		return null;
	}


}