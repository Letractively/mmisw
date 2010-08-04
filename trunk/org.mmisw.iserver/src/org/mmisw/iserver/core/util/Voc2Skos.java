package org.mmisw.iserver.core.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mmisw.ont.JenaUtil2;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFWriter;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.RDFS;

/**
 * Voc2Skos conversion utility.
 * 
 * @author Carlos Rueda
 */
public class Voc2Skos {

	/**
	 * Reads an ontology from a file that is assumed to be in CSV format.
	 *  
	 * 
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static OntModel loadModel(File file) throws IOException {
		Voc2Skos v2s = new Voc2Skos(file);
		try {
			Model m = v2s.convert();
			
			// wrap it in an OntModel and return it:
			OntModel ontModel = ModelFactory.createOntologyModel();
			ontModel.add(m);
			ontModel.setNsPrefixes(m);
			return ontModel;
		}
		finally {
			IOUtils.closeQuietly(v2s.is);
		}
	}
	
	/**
	 * Just a quick way to write the base model, which was the actual model
	 * generated. We had to wrap it in an OntModel to comply with the outside
	 * code that mainly handles OntModel's.
	 * 
	 * <p>
	 * NOTE: This method should be called with a model generated by {@link #loadModel(File)}.
	 * 
	 * @param ontModel  model generated by {@link #loadModel(File)}.
	 * @param file
	 * @param base
	 * @throws IOException
	 */
	public static void saveOntModelXML(OntModel ontModel, File file,
			String base) throws IOException {

		// extract the actual model generated.
		Model model = ontModel.getBaseModel();
		
		String xmlbase = null;
		String namespace = null;
		
		if ( base != null ) {
			xmlbase = JenaUtil2.removeTrailingFragment(base);
			namespace = JenaUtil2.appendFragment(base);
		}
		
		FileOutputStream out = new FileOutputStream(file);
		
		try {
			// NOTE When model is OntModel, the following generates a bunch of stuff
			RDFWriter writer = model.getWriter("RDF/XML-ABBREV");
			writer.setProperty("showXmlDeclaration", "true");
			writer.setProperty("relativeURIs", "same-document,relative");
			writer.setProperty("tab", "4");
			if ( xmlbase != null ) {
				writer.setProperty("xmlbase", xmlbase);
			}

			writer.write(model, out, namespace);
		}
		finally {
			IOUtils.closeQuietly(out);
		}
	}

	private static final Pattern ontologyUriPattern = Pattern.compile("\\s*ontologyUri\\s*=\\s*(.*)$");
	private static final Pattern classIdPattern = Pattern.compile("\\s*class\\.id\\s*=\\s*(.*)$");
	private static final Pattern classPrefLabelPattern = Pattern.compile("\\s*class\\.prefLabel\\s*=\\s*(.*)$");
	
	private final Log log = LogFactory.getLog(Voc2Skos.class);
	

	private FileInputStream is;
	private LineIterator lines;
	private int lineno = 0;

	private Model model;
	private Resource conceptSubClass;
	private int numConcepts = 0;
	
	
	
	private Voc2Skos(File file) throws IOException{
		this.is = new FileInputStream(file);
		lines = IOUtils.lineIterator(is, "utf8");
	}
	
	private Model convert() throws IOException {
		_debug("convert: start");
		
		
		String ontologyUri = "http://example.org";
		String classId = "UnnamedConcept";
		String classPrefLabel = "Unnamed Concept";
		
		// scan concept definition section; break where terms section begins
		String line = null;
		while ( lines.hasNext()  &&  (line = _nextLine()) != null ) {
			
			Matcher matcher;
			if ( (matcher = ontologyUriPattern.matcher(line)).matches() ) {
				String propValue = matcher.group(1);
				ontologyUri = propValue;
			}
			else if ( (matcher = classIdPattern.matcher(line)).matches() ) {
				String propValue = matcher.group(1);
				classId = propValue;
			}
			else if ( (matcher = classPrefLabelPattern.matcher(line)).matches() ) {
				String propValue = matcher.group(1);
				classPrefLabel = propValue;
			}
			else {
				break;
			}
		}

		if ( line == null ) {
			throw new IOException("Expecting terms section. Line: " +lineno);
		}

		// here we have the header line.
		String[] header = line.split(",");
		if ( header.length == 0 ) {
			throw new IOException("No header columns");
		}

		model = Skos.createModel();
		
		final String namespace = ontologyUri + "/";
		model.setNsPrefix("", namespace);
		
		String conceptUri = namespace + classId;
		String conceptLabel = classPrefLabel;
		conceptSubClass = Skos.addConceptSubClass(model, conceptUri, conceptLabel);
		
		_debug("ontologyUri: " +ontologyUri);
		_debug("namespace:   " +namespace);
		_debug("conceptUri:  " +conceptUri);

		// header[0] may be "uri" (ignoring case) or any other string.
		// If "uri", the values in first column will determine the complete URI of the term.
		// See below.
		//
		
		// props[0] is ignored--we want to keep symmetry in the subindexing
		Property[] props = new Property[header.length];
		
		// create datatype properties -- note that we start with 2nd column
		for ( int jj = 1; jj < header.length; jj++ ) {
			String colName = header[jj].trim();
			
			if ( colName.equals("skos:prefLabel") ) {
				props[jj] = Skos.prefLabel;
			}
			else if ( colName.equals("rdfs:comment") ) {
				props[jj] = RDFS.comment;
			}
			//
			// TODO: add other "built-in" properties -- and use a Map for them instead of these conditions.
			//
			else {
				// user-given property
				
				String propName = colName.replaceAll("\\s", "_"); // TODO complement correct propName
				String propUri = namespace + propName;
				props[jj] = Skos.addDatatypeProperty(model, conceptSubClass, propUri , colName);
				
				_debug("propUri:  " +propUri);
			}
		}

		//
		// Now, create the concepts.
		//
		
		while ( lines.hasNext()  &&  (line = _nextLine()) != null ) {
			String[] row = line.split(",");
			
			String ID = row[0].trim();
			
			if ( row.length == 0 ) {
				// should not happen.
				continue;
			}
			
			Resource concept;
			String conceptURI;
			if ( "uri".equalsIgnoreCase(header[0].trim()) ) {
				// conceptURI fully given by ID
				conceptURI = ID;
			}
			else {
				// conceptURI given by namespace and ID
				conceptURI = namespace + ID;
			}
			concept = _createConcept(conceptURI);
			_debug("conceptURI:  " +conceptURI);
			
			final int count = Math.min(row.length, header.length);
			
			for ( int jj = 1; jj < count; jj++ ) {
				Property prop = props[jj];
				String colValue = row[jj].trim();
				
				concept.addProperty(prop, colValue);
			}
		}
		
		_debug("convert: ontology created: " +numConcepts+ " concepts.");
		
		return model;
	}

	
	private void _debug(String msg) {
		if ( log.isDebugEnabled() ) {
			log.debug(msg);
		}
		System.out.println("!!!! " +msg);
	}

	private Resource _createConcept(String uri) {
		Resource concept = model.createResource(uri, conceptSubClass);
		numConcepts++;
		return concept;
	}

	private String _nextLine() {
		while ( lines.hasNext() ) {
			String line = lines.nextLine();
			lineno++;
			
			if ( line.matches("\\s*") ) {
				// ignore empty line:
				continue;
			}
			if ( line.matches("\\s*#.*") ) {
				// ignore comment line
				continue;
			}
			
			return line;
		}
		return null;
	}
	
	
	public static void main(String[] args) throws IOException {
		File file = new File("/Users/carueda/qqq.csv");
		OntModel model = loadModel(file);
		File fileOut = new File("/Users/carueda/qqq.rdf");
		String base = model.getNsPrefixURI("");
		Voc2Skos.saveOntModelXML(model, fileOut, base);
	}
}
