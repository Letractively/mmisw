package org.mmisw.ont;

import java.net.URISyntaxException;
import java.util.List;

import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.util.PrintUtil;

import edu.drexel.util.rdf.JenaUtil;


/**
 * Handles the "big" graph of all existing ontologies as returned by
 * {@link org.mmisw.ont.Db#getOntologies()}.
 * 
 * @author Carlos Rueda
 */
public class OntGraph {
	
	private final Log log = LogFactory.getLog(OntGraph.class);
	
	/** Load the unversioned form of the ontologies? 
	 * (Set to false to get the original behavior--load "versioned" ontologies).
	 */
	private static final boolean USE_UNVERSIONED = true;

	private final OntConfig ontConfig;
	private final Db db;
	
	/** the model with all the ontologies */
	private Model _model;
	
	private String aquaUploadsDir;

	
	/**
	 * Creates an instance of this helper.
	 * 
	 * @param ontConfig Used at initialization to obtain the "uploads" directory, where the
	 *        actual ontology files are located.
	 *        
	 * @param db The database helper.
	 */
	OntGraph(OntConfig ontConfig, Db db) {
		this.ontConfig = ontConfig;
		this.db = db;
	}

	public Model getModel() {
		return _model;
	}


	/**
	 * Initializes the graph.
	 * Does nothing if already initialized.
	 * @throws ServletException
	 */
	void init() throws ServletException {
		log.info("init called.");
		
		aquaUploadsDir = ontConfig.getProperty(OntConfig.Prop.AQUAPORTAL_UPLOADS_DIRECTORY);
		
		if ( _model == null ) {
			_doInitModel();
			log.info("init complete.");
		}
		else {
			log.debug("init: already initialized.");
		}
	}
	
	/**
	 * Reinitializes the graph.
	 * @throws ServletException
	 */
	void reinit() throws ServletException {
		log.info("reinit called.");
		_doInitModel();
		log.info("reinit complete.");
	}
	
	private void _doInitModel() throws ServletException {
		_model = ModelFactory.createDefaultModel();
		
		// get the list of (latest-version) ontologies:
		List<Ontology> onts = db.getOntologies();
		
		if ( log.isDebugEnabled() ) {
			log.debug("Using unversioned ontologies: " +USE_UNVERSIONED);
		}
		
		// 
		for ( Ontology ontology : onts ) {
			String full_path = aquaUploadsDir 
				+ "/" +ontology.file_path + "/" + ontology.filename;
		
			log.info("init: loading: " +full_path);
			
				
			if ( USE_UNVERSIONED ) {
				OntModel model = JenaUtil.loadModel(full_path, false);
				MmiUri mmiUri;
				try {
					mmiUri = new MmiUri(ontology.getUri());
				}
				catch (URISyntaxException e) {
					log.error("shouldn't happen", e);
					continue;
				}
				OntModel unversionedModel = UnversionedConverter.getUnversionedModel(model, mmiUri);
				_model.add(unversionedModel);
			}
			else {
				String absPath = "file:" + full_path;
				try {
					_model.read(absPath, "", null);
					
					// TODO processImports: true or false?
//					boolean processImports = false;
//					Model model_ = JenaUtil.loadModel(absPath, processImports);
//					_model.add(model_);
				} 
				catch (Exception e) {
					log.error("Unable to add " + absPath + " to model");
				}
			}
		}

		if ( false && log.isDebugEnabled() ) {
			log.debug("_listStatements:");
			StmtIterator iter = _model.listStatements();
			while (iter.hasNext()) {
				com.hp.hpl.jena.rdf.model.Statement sta = iter.nextStatement();
				Resource sjt = sta.getSubject();
				log.debug("      " + 
						PrintUtil.print(sjt)
						+ "   " +
						PrintUtil.print(sta.getPredicate().getURI())
						+ "   " +
						PrintUtil.print(sta.getObject().toString())
				);
			}
		}
	}

}