package org.mmisw.orrclient.core.voc2skos;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mmisw.ont.JenaUtil2;
import org.mmisw.ont.vocabulary.Skos;
import org.mmisw.orrclient.core.util.Utf8Util;
import org.mmisw.orrclient.core.util.csv.BaseParser;

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
	 * Reads a model from a text file in the format specified in issue #133, and
	 * returns it as an OntModel.
	 * 
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static OntModel loadOntModel(File file) throws Exception {
		Utf8Util.verifyUtf8(file);
		Model model = loadModel(file);
		OntModel ontModel = ModelFactory.createOntologyModel();
		ontModel.add(model);
		ontModel.setNsPrefixes(model);
		return ontModel;
	}
	
	/**
	 * Reads a model from a text file in the format specified in issue #133.
	 * 
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static Model loadModel(File file) throws IOException {
		Voc2Skos v2s = new Voc2Skos(file);
		v2s._convert();
		return v2s.model;
	}
	
	/**
	 * Saves a model in RDF/XML format.
	 * 
	 * @param model  the model.
	 * @param file
	 * @param base
	 * @throws IOException
	 */
	public static void saveModelXML(Model model, File file,
			String base) throws IOException {

		String xmlbase = null;
		String namespace = null;
		
		if ( base != null ) {
			xmlbase = JenaUtil2.removeTrailingFragment(base);
			namespace = JenaUtil2.appendFragment(base);
		}
		
		FileOutputStream out = new FileOutputStream(file);
		
		try {
			RDFWriter writer = model.getWriter("RDF/XML-ABBREV");
			writer.setProperty("showXmlDeclaration", "true");
			writer.setProperty("relativeURIs", "same-document,relative");
			writer.setProperty("tab", "4");
			if ( xmlbase != null ) {
				// NOTE about namespace for xmlbase, see ChangeLog.txt 2010-08-04
//				writer.setProperty("xmlbase", xmlbase);
				writer.setProperty("xmlbase", namespace);
			}

			writer.write(model, out, namespace);
		}
		finally {
			IOUtils.closeQuietly(out);
		}
	}


	/**
	 * Just a quick way to write the base model of the given OntModel. This base model
	 * was the actual model generated. We had to wrap it in an OntModel to comply with the outside
	 * code that mainly handles OntModel's.
	 * 
	 * <p>
	 * NOTE: This method should be called with a model generated by {@link #loadOntModel(File)}.
	 * 
	 * @param ontModel  model generated by {@link #loadOntModel(File)}.
	 * @param file
	 * @param base
	 * @throws IOException
	 */
	public static void saveOntModelXML(OntModel ontModel, File file,String base) throws IOException {

		// extract the actual model generated.
		Model model = ontModel.getBaseModel();
		
		saveModelXML(model, file, base);
		
		// If we passed the given ontModel, ie., make the call
		//    saveModelXML(ontModel, file, base);
		// instead, the output file would contain a bunch of stuff related with the fact
		// it's an ontology (as opposed to a more basic RDF model).

	}

	
	/** standard properties that are recognized by its typical prefix in the preamble and 
	 * in the header columns.
	 * NOTE: Not exhaustive; I'm choosing the most obvious ones, taking into account that
	 * they can have string as the range.
	 */
	private static final Map<String,Property> STD_PROPS = new HashMap<String,Property>();
	static {
		Property[] skosProps = {
			Skos.prefLabel, Skos.altLabel, Skos.hiddenLabel, 
			
			Skos.definition, Skos.changeNote, Skos.editorialNote, Skos.example, 
			Skos.historyNote, Skos.note, Skos.scopeNote
		};
		for ( Property prop : skosProps ) {
			STD_PROPS.put("skos:" +prop.getLocalName(), prop);
		}
		
		Property[] rdfsProps = {
				RDFS.label, RDFS.comment, RDFS.isDefinedBy, RDFS.seeAlso,	
		};
		for ( Property prop : rdfsProps ) {
			STD_PROPS.put("rdfs:" +prop.getLocalName(), prop);
		}
	}

	private static final String KEY_ONTOLOGY_URI = "ontologyURI";
	private static final String KEY_CLASS = "class";
	private static final String KEY_INDENT_STRING = "indent.string";
	private static final String KEY_INDENT_PROPERTY = "indent.property";
	private static final String KEY_SEPARATOR = "separator";

	/** the params that are recognized in the preamble: 
	 * a few key properties and all the standard properties above 
	 */
	private static final List<String> RECOGNIZED_PARAMS_IN_PREAMBLE = 
		new ArrayList<String>(Arrays.asList(
				KEY_ONTOLOGY_URI,
				KEY_CLASS,
				KEY_INDENT_STRING,
				KEY_INDENT_PROPERTY,
				KEY_SEPARATOR
	));
	static {
		RECOGNIZED_PARAMS_IN_PREAMBLE.addAll(STD_PROPS.keySet());
	}

	private static final Map<String,Property> VALID_RELATIONS = new LinkedHashMap<String,Property>();
	static {
		VALID_RELATIONS.put("skos:narrower", Skos.narrower);
		VALID_RELATIONS.put("skos:broader", Skos.broader);
	}

	
	/** pattern for defs in the preamble section:  something = something */
	private static final Pattern PARAM_PATTERN = Pattern.compile("\\s*([^\\s=]+)\\s*=\\s*(.*)$");


	
	////////////////////////////////////////////////////////////////////////////
	// instance.
	////////////////////////////////////////////////////////////////////////////
	
	
	private final Log log = LogFactory.getLog(Voc2Skos.class);
	
	
	private BaseParser parser;
	private String[] record;

	private Map<String,String> givenParams;
	
	private String[] header;

	private Map<String,String> workParams;
	
	private Property[] props;
	
	private Model model;
	private Resource conceptSubClass;
	private int numConcepts = 0;
	
	
	
	private Voc2Skos(File file) throws IOException{
		parser = BaseParser.createParser(file);
		givenParams = new LinkedHashMap<String,String>();
		workParams = new LinkedHashMap<String,String>();
	}
	
	private void _convert() throws IOException {
		_debug("_convert: start");
		try {
			_doConvert();
		}
		finally {
			parser.close();
		}
	}
	
	private void _doConvert() throws IOException {
		_setDefaultWorkParams();
		
		_parsePreamble();
		_prepareModel();
		_parseHeader();
		_prepareProperties();
		_parseTerms();
	}
	
	private void _setDefaultWorkParams() {
		workParams.put(KEY_ONTOLOGY_URI, "http://example.org");
		workParams.put(KEY_CLASS, "UnnamedConcept");
	}

	/**
	 * scans preamble section; return line where terms section begins, or null.
	 * @throws IOException 
	 */
	private void _parsePreamble() throws IOException {
		while ( parser.hasNext() ) {
			record = parser.getNext();
			
			if ( record.length == 1 ) {
				Matcher matcher = PARAM_PATTERN.matcher(record[0]);
				if ( matcher.matches() ) {
					String paramName = matcher.group(1);
					String paramValue = matcher.group(2);
					_putGivenParam(paramName, paramValue);
				}
			}
			else {
				break;
			}
		}
		_debugParams("Given params: ", givenParams);
		
		workParams.putAll(givenParams);
		
		workParams.put("namespace", JenaUtil2.appendFragment(workParams.get(KEY_ONTOLOGY_URI)));
		
		_debugParams("Work params: ", workParams);
		
		if ( record == null ) {
			throw parser.error("Expecting terms section");
		}
	}

	private void _prepareModel() {
		final String classId = workParams.get(KEY_CLASS);
		final String namespace = workParams.get("namespace");
		final String conceptUri = namespace + classId;
		
		model = Skos.createModel();
		model.setNsPrefix("", namespace);
		conceptSubClass = Skos.addConceptSubClass(model, conceptUri);
		
		// associate indicated standard properties:
		for ( String paramName : workParams.keySet() ) {
			Property stdProp = STD_PROPS.get(paramName);
			if ( stdProp != null ) {
				conceptSubClass.addProperty(stdProp, workParams.get(paramName));
			}
		}
		
		_debug("namespace:   " +namespace);
		_debug("conceptUri:  " +conceptUri);
		
	}
	
	private void _parseHeader() throws IOException {
		header = record;
		if ( header.length == 0 ) {
			throw parser.error("No header columns");
		}
	}

	private void _prepareProperties() {

		// header[0] may be "uri" (ignoring case) or any other string.
		// If "uri", the values in first column will determine the complete URI of the term.
		// See below.
		//
		
		// Note, props[0] not used--we want to keep symmetry in the subindexing
		props = new Property[header.length];
		
		// create datatype properties -- note that we start with 2nd column
		for ( int jj = 1; jj < header.length; jj++ ) {
			String colName = header[jj].trim();
			
			if ( STD_PROPS.get(colName) != null ) {
				props[jj] = STD_PROPS.get(colName);
			}
			else {
				// user-given property.
				
				String propName = colName.replaceAll("\\s", "_"); // TODO complement correct propName
				String propUri = workParams.get("namespace") + propName;
				props[jj] = Skos.addDatatypeProperty(model, conceptSubClass, propUri , colName);
				
				_debug("propUri:  " +propUri);
			}
		}
	}
	
	
	/** Responsible of creating the relations between concepts
	 * according to the indented structure of the input.
	 */
	private class HierarchyMan {
		private String indentString = workParams.get(KEY_INDENT_STRING);
		private Stack<Resource> stack = indentString == null ? null : new Stack<Resource>();
		private Property relation = indentString == null ? null : VALID_RELATIONS.get(workParams.get(KEY_INDENT_PROPERTY));
		
		/** processes one more concept */
		void processConcept(String givenID, Resource concept) throws IOException {
			if ( indentString == null ) {
				return;
			}
			
			int level = _getLevel(givenID);
			
			if ( stack.size() == level ) {
				if ( stack.size() > 0 ) {
					stack.pop();
					if ( stack.size() > 0 ) {
						Resource parent = stack.peek();
						_addRelation(parent, concept);
					}
				}
			}
			else if ( stack.size() < level ) {
				if ( stack.size() + 1 != level ) {
					throw parser.error("Invalid indentation: new level too deep");
				}
				if ( stack.size() > 0 ) {
					Resource parent = stack.peek();
					_addRelation(parent, concept);
				}
			}
			else { //  stack.size() > level 
				while ( stack.size() > level ) {
					stack.pop();
				}
				if ( stack.size() > 0 ) {
					stack.pop();
				}
				if ( stack.size() > 0 ) {
					Resource parent = stack.peek();
					_addRelation(parent, concept);
				}
			}
			stack.push(concept);
		}

		private int _getLevel(String givenID) {
			int level = 1;
			while ( givenID.startsWith(indentString) ) {
				givenID = givenID.substring(indentString.length());
				level++;
			}
//			_debug(" LEVEL: " +level+ "  " +givenID);
			return level;
		}
		
		private void _addRelation(Resource parent, Resource concept) {
			_debug("RELATION: " +parent.getLocalName()+ "  " +relation.getLocalName()+ "  " +concept.getLocalName());
			parent.addProperty(relation, concept);
		}
	}
	
	private void _parseTerms() throws IOException {
		// Now, create the concepts.
		
		HierarchyMan hierMan = new HierarchyMan();
		
		while ( parser.hasNext() ) {
			String[] row = parser.getNext();
			
			// keep spaces from the first column for indentation analysis:
			final String givenID = row[0];
			
			// and trim the string for purposes of the ID:
			final String ID = givenID.trim();
			
			Resource concept;
			String conceptURI;
			if ( "uri".equalsIgnoreCase(header[0].trim()) ) {
				// conceptURI fully given by ID
				conceptURI = ID;
			}
			else {
				// conceptURI given by namespace and ID
				conceptURI = workParams.get("namespace") + ID;
			}
			concept = _createConcept(conceptURI);
//			_debug("conceptURI:  " +conceptURI);
			
			final int count = Math.min(row.length, header.length);
			
			for ( int jj = 1; jj < count; jj++ ) {
				Property prop = props[jj];
				String colValue = row[jj].trim();
				
				concept.addProperty(prop, colValue);
			}
			
			hierMan.processConcept(givenID, concept);
		}
		
		_debug("convert: ontology created: " +numConcepts+ " concepts.");
	}

	private void _putGivenParam(String paramName, String paramValue) throws IOException {
		paramName = _unquote(paramName);
		
		if ( ! RECOGNIZED_PARAMS_IN_PREAMBLE.contains(paramName) ) {
			parser.error("Unrecognized parameter in preamble: " +paramName+ 
					"\nValid parameters in preamble are: " +RECOGNIZED_PARAMS_IN_PREAMBLE);
		}
		paramValue = _unquote(paramValue);
		
		if ( KEY_INDENT_STRING.equals(paramName) ) {
			if ( paramValue.matches(".*\\w.*") ) {
				throw parser.error("indent.string should not contain any alphanumeric characters: \"" +paramValue+ "\"");
			}
		}
		else if ( KEY_SEPARATOR.equals(paramName) ) {
			if ( paramValue.length() != 1 ) {
				throw parser.error("separator string must be a single character: \"" +paramValue+ "\"");
			}
			parser.setSeparator(paramValue.charAt(0));
		}
		else if ( KEY_INDENT_PROPERTY.equals(paramName) ) {
			if ( ! VALID_RELATIONS.keySet().contains(paramValue) ) {
				throw parser.error("indent.property should be one of: " +VALID_RELATIONS.keySet());
			}
		}

		givenParams.put(paramName, paramValue);
	}

	private String _unquote(String str) {
		str = str.trim();
		while ( str.length() > 1 && str.charAt(0) == '"' && str.charAt(str.length() - 1) == '"' ) {
			str = str.substring(1, str.length() - 1);
		}
		return str;
	}
	
	private void _debugParams(String label, Map<String,String> params) {
		_debug(label);
		for ( Entry<String, String> entry : params.entrySet() ) {
			_debug("\t" +entry.getKey()+ " = [" +entry.getValue()+ "]");
		}
	}

	private void _debug(String msg) {
		if ( log.isDebugEnabled() ) {
			log.debug(msg);
		}
//		System.out.println("!!!! " +msg);
	}

	private Resource _createConcept(String uri) {
		Resource concept = model.createResource(uri, conceptSubClass);
		numConcepts++;
		return concept;
	}
}
