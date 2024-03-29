package org.mmisw.mmiorr.client.test;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFWriter;


/**
 * some support utilities for the tests 
 * @author Carlos Rueda
 */
public class Utils {
	
	/** gets a system property */
	public static String getRequiredSystemProperty(String key) {
		String val = System.getProperty(key);
		if ( val == null || val.trim().length() == 0 ) {
			throw new IllegalArgumentException("Required system property '" +key+ "' not specified");
		}
		return val;
	}

	/** reads an ontology model from a file */
	public static OntModel readOntModel(File file) throws IOException {
		String uriModel = file.toURI().toString();
		OntModel model = ModelFactory.createOntologyModel();
		model.setDynamicImports(false);
		model.getDocumentManager().setProcessImports(false);
		model.read(uriModel);
		return model;
	}
	
	/** reads an ontology model from a string */
	public static OntModel readOntModel(String contents) throws IOException {
		OntModel model = ModelFactory.createOntologyModel();
		model.setDynamicImports(false);
		model.getDocumentManager().setProcessImports(false);
		model.read(new StringReader(contents), null);
		return model;
	}

	/** reads a term model from a string */
	public static Model readTermModel(String contents) throws IOException {
		return readModel(contents);
	}
	
	/** reads an RDF model from a string */
	public static Model readModel(String contents) throws IOException {
		Model model = ModelFactory.createDefaultModel();
		model.read(new StringReader(contents), null);
		return model;
	}
	
	/**
	 * Serializes a model
	 */	
	public static String getOntModelAsString(Model model, String lang) {
		StringWriter sw = new StringWriter();
		RDFWriter writer = model.getWriter(lang);
		String baseUri = null;
		String uriForEmptyPrefix = model.getNsPrefixURI("");
		if ( uriForEmptyPrefix != null ) {
			// remove trailing fragment, if any
			baseUri = uriForEmptyPrefix.replaceAll("(/|#)+$", "");
			writer.setProperty("xmlbase", baseUri);
		}
		writer.setProperty("showXmlDeclaration", "true");
		writer.setProperty("relativeURIs", "same-document");
		writer.setProperty("tab", "4");
		writer.write(model, sw, baseUri);
		return sw.getBuffer().toString();

	}

}
