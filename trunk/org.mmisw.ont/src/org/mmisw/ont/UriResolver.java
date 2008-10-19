package org.mmisw.ont;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mmisw.ont.sparql.SparqlDispatcher;
import org.mmisw.ont.util.Util;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.util.PrintUtil;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;
import com.hp.hpl.jena.vocabulary.RDFS;

import edu.drexel.util.rdf.JenaUtil;


/**
 * The "ont" service to resolve ontology URIs.
 * 
 * @author Carlos Rueda
 */
public class UriResolver extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	
	private static final String VERSION = "0.1.1 (20081018)";
	private static final String TITLE = "MMI Ontology URI resolver. Version " +VERSION;

	private final Log log = LogFactory.getLog(UriResolver.class);

	private final OntConfig ontConfig = new OntConfig();
	private final Db db = new Db(ontConfig);
	private final OntGraph ontGraph = new OntGraph(ontConfig, db);


	public void init() throws ServletException {
		log.info(TITLE+ ": initializing");
		
		try {
			ontConfig.init(getServletConfig());
			db.init();
			ontGraph.initRegistry();
		} 
		catch (Exception ex) {
			log.error("Cannot initialize: " +ex.getMessage(), ex);
			throw new ServletException("Cannot initialize", ex);
		}
	}
	
	public void destroy() {
		log.info(TITLE+ ": destroy called.\n\n");
	}
	
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		// first, see if there are any testing requests to dispatch 
		
		// show request info?
		if ( Util.yes(request, "showreq")  ) {
			_showReq(request, response);
		} 
		
		// dispatch list of ontologies?
		else if ( Util.yes(request, "list")  ) {
			_doListOntologies(request, response);
		}
		
		// dispatch a sparql-query?
		else if ( Util.yes(request, "sparql")  ) {
			new SparqlDispatcher().execute(request, response, ontGraph);
		}
		
		
		// reload graph?
		else if ( Util.yes(request, "_reload")  ) {
			ontGraph.reInitRegistry();
		}
		
		// dispatch a db-query?
		else if ( Util.yes(request, "dbquery")  ) {
			_doDbQuery(request, response);
		}
		
		// resolve URI?
		else if ( _resolveUri(request, response)  ) {
			// OK, no more to do here.
		}
		
		// Else, try to resolve the requested resource.
		// Note, since I'm using <url-pattern>/*</url-pattern> in web.xml, *everything* 
		// gets dispatched through this servlet, so I have to resolve other possible resources.
		else {
			String path = request.getPathTranslated();
			File file = new File(path);
			if ( !file.canRead() || file.isDirectory() ) {
				if ( log.isDebugEnabled() ) {
					log.debug(path+ ": not found or cannot be read");
				}
				response.sendError(HttpServletResponse.SC_NOT_FOUND, 
						request.getRequestURI()+ ": not found");

				return;
			}
			
			String mime = getServletContext().getMimeType(path);
			if ( mime != null ) {
				response.setContentType(mime);
			}
			
			if ( log.isDebugEnabled() ) {
				log.debug(path+ ": FOUND. " +
						(mime != null ? "Mime type set to: " +mime : "No Mime type set.")
				);
			}

			FileInputStream is = new FileInputStream(file);
			ServletOutputStream os = response.getOutputStream();
			IOUtils.copy(is, os);
			os.close();
		}
	}

	/**
	 * Resolves the ontology identified by its URI as indicated by <code>request.getRequestURL()</code>.
	 * 
	 * <p>
	 * This is the main operation in this servlet.
	 * 
	 * @param request
	 * @param response
	 * 
	 * @return true for dispatch completed here; false otherwise.
	 * 
	 * @throws ServletException 
	 * @throws IOException 
	 */
	private boolean _resolveUri(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		
		final String fullRequestedUri = request.getRequestURL().toString();
		
		// if the "info" parameter is included, show some info about the URI parse
		// and the ontology from the database (but do not serve the contents)
		boolean info = Util.yes(request, "info");
		PrintWriter out = null;    // only used iff info == true.
		
		if ( info ) {
			if ( log.isDebugEnabled() ) {
				log.debug("_resolveUrI: starting 'info' response.");
			}
			
			// start the response page:
			response.setContentType("text/html");
			out = response.getWriter();
	        out.println("<html>");
	        out.println("<head>");
	        out.println("<title>" +TITLE+ "</title>");
	        out.println("<link rel=stylesheet href=\"" +request.getContextPath()+ "/main.css\" type=\"text/css\">");
	        out.println("</head>");
	        out.println("<body>");
			out.println("<b>" +TITLE+ "</b><br/><br/>");
			out.println(" Full requested URI: <code>" + fullRequestedUri + "</code> <br/><br/>");
		}
		
		// parse the given URI:
		final String requestedUri = request.getRequestURI();
		final String contextPath = request.getContextPath();
		MmiUri mmiUri = null;
		try {
			mmiUri = new MmiUri(fullRequestedUri, requestedUri, contextPath);
		}
		catch (URISyntaxException e) {
			if ( info ) {
				out.println("<font color=\"red\">ERROR: " +e.getReason()+ "</font><br/>");
		        out.println("</body>");
		        out.println("</html>");
				return true;   // dispatched here.
			}
			return false;   // not dispatched here.
		}
		
		String ontologyUri = mmiUri.getOntologyUri();
	
		if ( info  ) {
			// show the parse result:
			String authority = mmiUri.getAuthority();
			String topic = mmiUri.getTopic();
			String term = mmiUri.getTerm();
			out.println("Parse result: OK<br/>");
			out.println("<pre>");
			out.println("       Ontology URI: " + ontologyUri);
			out.println("          authority: " + authority);
			out.println("              topic: " + topic);
			out.println("               Term: " + term);
			out.println("</pre>");
		}

		// obtain info about the ontology:
    	Ontology ontology = db.getOntology(ontologyUri);
		
		if ( info  ) {
			out.println("<br/>Database result:<br/> ");
		}
		
    	if ( ontology == null ) {
			if ( info  ) {
				out.println(ontologyUri+ ": <font color=\"red\">Not found.</font> <br/>");
			}
    		// if topic has no extension, try with ".owl"
    		if ( mmiUri.getTopic().indexOf('.') < 0 ) {
    			if ( info  ) {
    				out.println("Trying with .owl extension... <br/>");
    			}
    			String withExt = mmiUri.getOntologyUriWithTopicExtension(".owl");
    			ontology = db.getOntology(withExt);
    			if ( ontology != null ) {
    				if ( info  ) {
    					out.println(withExt+ ": <font color=\"green\">Found.</font> <br/>");
    				}    		
    				ontologyUri = withExt;
    			}
    			else {
    				out.println(withExt+ ": <font color=\"red\">Not found.</font> <br/>");
    			}
    		}
    	}
    	
		if ( ontology == null ) {
			if ( info  ) {
		        out.println("</body>");
		        out.println("</html>");
				return true;    // dispatched.
			}
			else {
				return false;   // not dispatched here.
			}
		}
		
		// prepare info about the path to the file on disk:
		String full_path = ontConfig.getProperty(OntConfig.Prop.AQUAPORTAL_UPLOADS_DIRECTORY) 
			+ "/" +ontology.file_path + "/" + ontology.filename;
		File file = new File(full_path);

		if ( info  ) {
			// report the db info and whether the file can be read or not:
			out.println(" Ontology entry FOUND: <br/>");
			out.println("<pre>");
			out.println("                 id: " + ontology.id);
			out.println("        ontology_id: " + ontology.ontology_id);
			out.println("          file_path: " + ontology.file_path);
			out.println("           filename: " + ontology.filename);
			out.println("</pre>");
			out.println(" Full path: <code>" + full_path + "</code> ");
			out.println(" Can read it: <code>" + file.canRead() + "</code> <br/>");
			
			if ( file.canRead() ) {
				out.println("<br/>");
				
				String uriFile = file.toURI().toString();
				Model model = _loadModel(uriFile);
	
				if ( mmiUri.getTerm().length() > 0 ) {
					_showTermInfo(mmiUri, model, out);
				}
				else {
					_showAllTerms(mmiUri, model, out);
				}
			}
		}
		else {
			if ( file.canRead() ) {
				String term = mmiUri.getTerm();
				if ( term.length() > 0 ) {
					String uriFile = file.toURI().toString();
					Model model = _loadModel(uriFile);
					
					// TODO "text/html" for now
					String termContents = _resolveTerm(request, mmiUri, model);
					StringReader is = new StringReader(termContents);
					response.setContentType("text/html");
					ServletOutputStream os = response.getOutputStream();
					IOUtils.copy(is, os);
					os.close();
				}
				else {
					// respond with the contents of the file with contentType set to RDF+XML 
					response.setContentType("Application/rdf+xml");
					FileInputStream is = new FileInputStream(file);
					ServletOutputStream os = response.getOutputStream();
					IOUtils.copy(is, os);
					os.close();
				}
			}
			else {
				// This should not happen.
				// Log the error and respond with a NotFound error:
				String msg = full_path+ ": internal error: uploaded file ";
				msg += file.exists() ? "exists but cannot be read." : "not found.";
				msg += "Please, report this bug.";
				log.error(msg, null);
				response.sendError(HttpServletResponse.SC_NOT_FOUND, msg); 
			}
		}
		
		return true;   // dispatched here.
	}
	

	private void _showAllTerms(MmiUri mmiUri, Model model, PrintWriter out) {
		out.printf(" All subjects in the model:<br/>%n"); 
		out.println("<table class=\"inline\">");
		out.printf("<tr>%n");
		out.printf("<th>Subject</th> <th>Info</th> <th>Name</th>%n");
		out.printf("</tr>%n");

		ResIterator iter = model.listSubjects();
		while (iter.hasNext()) {
			Resource elem = iter.nextResource();
			String elemUri = elem.getURI();
			if ( elemUri != null ) {
				String elemUriSlash = elemUri.replace('#' , '/');
				out.printf("<tr>%n");
				out.printf("<td> <a href=\"%s\">%s</a> </td> %n", elemUriSlash, elemUriSlash); 
				out.printf("<td> <a href=\"%s?info\">info</a> </td> %n", elemUriSlash); 
				out.printf("<td> %s </td> %n", elem.getLocalName()); 
				out.printf("</tr>%n");
			}
		}
		out.println("</table>");
	}

	/**
	 * Shows information about the requested term.
	 * @param mmiUri
	 * @param file
	 * @param out
	 */
	private void _showTermInfo(MmiUri mmiUri, Model model, PrintWriter out) {
		String term = mmiUri.getTerm();
		assert term.length() > 0 ;
		
		// construct URI of term with "#" separator
		String termUri = mmiUri.getTermUri(true, "#");
		Resource termRes = model.getResource(termUri);

		if ( termRes == null ) {
			// try with "/" separator
			termUri = mmiUri.getTermUri(true, "/");
			termRes = model.getResource(termUri);
		}
		
		if ( termRes == null ) {
			out.println("   No resource found for URI: " +termUri);
			return;
		}
		
		com.hp.hpl.jena.rdf.model.Statement labelRes = termRes.getProperty(RDFS.label);
		String label = labelRes == null ? null : ""+labelRes.getObject();
		
		out.println("<pre>");
		out.println("   term resource: " +termRes);
		out.println("           label: " +label);
		out.println("    getLocalName: " +termRes.getLocalName());
		

		if ( true ) { // get all about the term
			out.println("\n    All about: " +termRes.getURI());
			StmtIterator iter = model.listStatements(termRes, (Property) null, (Property) null);
			while (iter.hasNext()) {
				com.hp.hpl.jena.rdf.model.Statement sta = iter.nextStatement();
				out.printf("      %30s   %s%n", 
						PrintUtil.print(sta.getPredicate().getURI()),
						PrintUtil.print(sta.getObject().toString())
				);
			}
		}
		
		if ( true ) { // test for subclasses
			out.println("\n    Subclasses of : " +termRes.getURI());
			StmtIterator iter = model.listStatements(null, RDFS.subClassOf, termRes);
			if  ( iter.hasNext() ) {
				while ( iter.hasNext() ) {
					com.hp.hpl.jena.rdf.model.Statement sta = iter.nextStatement();
					out.println("  " + PrintUtil.print(sta.getSubject().getURI()));
				}
			}
			else {
				out.println("        (none)");
			}
		}
		

		if ( model instanceof OntModel ) {
			OntModel ontModel = (OntModel) model;
			out.println("    Individuals:");
			ExtendedIterator iter = ontModel.listIndividuals(termRes);
			while ( iter.hasNext() ) {
				Resource indiv = (Resource) iter.next();
				out.println("        " +indiv.getURI());
			}
		}
		
		out.println("</pre>");
	}		

	private String _resolveTerm(HttpServletRequest request, MmiUri mmiUri, Model model) {
		String term = mmiUri.getTerm();
		assert term.length() > 0 ;
		
		// construct URI of term with "#" separator
		String termUri = mmiUri.getTermUri(true, "#");
		Resource termRes = model.getResource(termUri);

		if ( termRes == null ) {
			// try with "/" separator
			termUri = mmiUri.getTermUri(true, "/");
			termRes = model.getResource(termUri);
		}
		
		if ( termRes == null ) {
			return null; // Not found.
		}
		
		StringWriter strWriter = new StringWriter();
		PrintWriter out = new PrintWriter(strWriter);
        out.println("<html>");
        out.println("<head>");
        out.println("<title>" +termUri+ "</title>");
        out.println("<link rel=stylesheet href=\"" +request.getContextPath()+ "/main.css\" type=\"text/css\">");
        out.println("</head>");
        out.println("<body>");
        out.println("<i>temporary response</i>");
		
		com.hp.hpl.jena.rdf.model.Statement labelRes = termRes.getProperty(RDFS.label);
		String label = labelRes == null ? null : ""+labelRes.getObject();
		
		out.println("<pre>");
		out.println("   term resource: " +termRes);
		out.println("           label: " +label);
		out.println("    getLocalName: " +termRes.getLocalName());
		

		if ( true ) { // get all about the term
			out.println("\n    All about: " +termRes.getURI());
			StmtIterator iter = model.listStatements(termRes, (Property) null, (Property) null);
			while (iter.hasNext()) {
				com.hp.hpl.jena.rdf.model.Statement sta = iter.nextStatement();
				out.printf("      %30s   %s%n", 
						PrintUtil.print(sta.getPredicate().getURI()),
						PrintUtil.print(sta.getObject().toString())
				);
			}
		}
		
		if ( true ) { // test for subclasses
			out.println("\n    Subclasses of : " +termRes.getURI());
			StmtIterator iter = model.listStatements(null, RDFS.subClassOf, termRes);
			if  ( iter.hasNext() ) {
				while ( iter.hasNext() ) {
					com.hp.hpl.jena.rdf.model.Statement sta = iter.nextStatement();
					out.println("  " + PrintUtil.print(sta.getSubject().getURI()));
				}
			}
			else {
				out.println("        (none)");
			}
		}
		

		if ( model instanceof OntModel ) {
			OntModel ontModel = (OntModel) model;
			out.println("    Individuals:");
			ExtendedIterator iter = ontModel.listIndividuals(termRes);
			while ( iter.hasNext() ) {
				Resource indiv = (Resource) iter.next();
				out.println("        " +indiv.getURI());
			}
		}
		
        out.println("</pre>");
        out.println("</body>");
        out.println("</html>");
        
        return strWriter.toString();
	}


	/**
	 * Loads a model.
	 * @param uriModel
	 * @return
	 */
	private Model _loadModel(String uriModel) {
		Model model = JenaUtil.loadModel(uriModel, false);
		
		return model;
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		super.doGet(request, response);
	}
	
	
	
	@SuppressWarnings("unchecked")
	private void _showReq(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        out.println("<html>");
        out.println("<head>");
        out.println("<title>Show req</title>");
        out.println("<link rel=stylesheet href=\"" +request.getContextPath()+ "/main.css\" type=\"text/css\">");
        out.println("</head>");
        out.println("<body>");
        out.println("<pre>");
        
        out.println("request.getRequestURL()         = " + request.getRequestURL()  );
        out.println("request.getRequestURI()         = " + request.getRequestURI()  );
        out.println("request.getQueryString()        = " + request.getQueryString()  );
        
        out.println("request.getParameterMap()       = " + request.getParameterMap()  );
		Map<String, String[]> params = Util.getParams(request);
		for ( String key: params.keySet() ) {
			out.println("    " +key+ " => " + Arrays.asList(params.get(key))  );	
		}
        
        out.println("request.getContextPath()        = " + request.getContextPath() ); 
        out.println("request.getMethod()             = " + request.getMethod()  ); 
        out.println("request.getPathInfo()           = " + request.getPathInfo()  ); 
        out.println("request.getPathTranslated()     = " + request.getPathTranslated()  ); 
        out.println("request.getRemoteUser()         = " + request.getRemoteUser()  );
        out.println("request.getRequestedSessionId() = " + request.getRequestedSessionId()  );
        out.println("request.getServletPath()        = " + request.getServletPath()  );
        out.println("request.getAttributeNames()     = " + request.getAttributeNames()  ); 
        out.println("request.getCharacterEncoding()  = " + request.getCharacterEncoding()  );
        out.println("request.getContentLength()      = " + request.getContentLength()  );
        out.println("request.getContentType()        = " + request.getContentType()  );
        out.println("request.getProtocol()           = " + request.getProtocol()  );
        out.println("request.getRemoteAddr()         = " + request.getRemoteAddr()  );
        out.println("request.getRemoteHost()         = " + request.getRemoteHost()  ); 
        out.println("request.getScheme()             = " + request.getScheme()  );
        out.println("request.getServerName()         = " + request.getServerName()  );
        out.println("request.getServerPort()         = " + request.getServerPort()  );
        out.println("request.isSecure()              = " + request.isSecure()  ); 
        
        out.println("request. headers             = ");
        Enumeration hnames = request.getHeaderNames();
        while ( hnames.hasMoreElements() ) {
        	Object hname = hnames.nextElement();
        	out.print("        " +hname+ " : ");
        	Enumeration hvals = request.getHeaders(hname.toString());
        	String sep = "";
            while ( hvals.hasMoreElements() ) {
            	Object hval = hvals.nextElement();
				out.println(hval + sep);
				sep = "  ;  ";
            }
            out.println();
        }        

        out.println("</pre>");
        out.println("</body>");
        out.println("</html>");
	}


	
	private void _doDbQuery(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			Connection _con = db.getConnection();
			Statement _stmt = _con.createStatement();
			String table = Util.getParam(request, "table", "ncbo_ontology");
			int limit = Integer.parseInt(Util.getParam(request, "limit", "500"));

			String query = "select * from " +table+ "  limit " +limit;
			
			ResultSet rs = _stmt.executeQuery(query);
			
			response.setContentType("text/html");
	        PrintWriter out = response.getWriter();
	        out.println("<html>");
	        out.println("<head>");
	        out.println("<link rel=stylesheet href=\"" +request.getContextPath()+ "/main.css\" type=\"text/css\">");
	        out.println("<title>" +query+ "</title>");
	        out.println("</head>");
	        out.println("<body>");
	        out.println("<code>" +query+ "</code>");
	        out.println("<table class=\"inline\">");

			
	        ResultSetMetaData md = rs.getMetaData();
	        int cols = md.getColumnCount();
	        out.println("<tr>");
	        for (int i = 0; i < cols; i++) {
	        	out.println("<th>");
	        	out.println(md.getColumnLabel(i+1));
	            out.println("</th>");
	        }
	        out.println("</tr>");

	        while ( rs.next() ) {
	        	out.println("<tr>");
	        	for (int i = 0; i < cols; i++) {
		        	out.println("<td>");
		        	out.println(rs.getObject(i+1));
		            out.println("</td>");
	        	}
	        	out.println("</tr>");
	        }

		} 
		catch (SQLException e) {
			throw new ServletException(e);
		}
	}
	
	private void _doListOntologies(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			Connection _con = db.getConnection();
			Statement _stmt = _con.createStatement();
			String table = "v_ncbo_ontology";
			int limit = Integer.parseInt(Util.getParam(request, "limit", "500"));

			String query = 
				"select id, ontology_id, user_id, urn " +
				"from " +table+ "  limit " +limit;
			
			ResultSet rs = _stmt.executeQuery(query);
			
			response.setContentType("text/html");
	        PrintWriter out = response.getWriter();
	        out.println("<html>");
	        out.println("<head> <link rel=stylesheet href=\"" +request.getContextPath()+ "/main.css\" type=\"text/css\"> </head>");
	        out.println("<title>" +query+ "</title>");
	        out.println("</head>");
	        out.println("<body>");
	        out.println("<code>" +query+ "</code>");
	        out.println("<table class=\"inline\">");

			
	        ResultSetMetaData md = rs.getMetaData();
	        int cols = md.getColumnCount();
	        int idxUrn = -1;
	        out.println("<tr>");
	        for (int i = 0; i < cols; i++) {
	        	out.println("<th>");
	        	String colLabel = md.getColumnLabel(i+1);
				out.println(colLabel );
	            out.println("</th>");
	            if ( "urn".equals(colLabel) ) {
	            	idxUrn = i;
	            }
	        }
	        out.println("</tr>");

	        while ( rs.next() ) {
	        	out.println("<tr>");
	        	for (int i = 0; i < cols; i++) {
		        	out.println("<td>");
		        	Object val = rs.getObject(i+1);
		        	if ( val != null ) {
		        		if ( idxUrn == i ) {
		        			out.println("<a href=\"" +val+ "\">" +val+ "</a>");
		        		}
		        		else {
		        			out.println(val);
		        		}
		        	}
		            out.println("</td>");
	        	}
	        	out.println("</tr>");
	        }

		} 
		catch (SQLException e) {
			throw new ServletException(e);
		}
	}

}
