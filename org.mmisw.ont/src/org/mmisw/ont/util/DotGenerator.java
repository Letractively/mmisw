package org.mmisw.ont.util;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.OWL;


/**
 * Generates dot for a given ontology.
 * 
 * <p>
 * Preliminary implementation, functional but needs lots of code clean up.
 * 
 * @author Carlos Rueda
 * @version $Id$
 */
public class DotGenerator {

	private static String PREFIXES =
		"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
		"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
		"PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
	;


	private Model ontModel;
	private PrintWriter pw;
	
	private boolean includeLegend = false;
	
	/** 
	 * Creates a dot generator
	 * 
	 * @param ontModel The model to read
	 * @param ontologyUri corresponding URI
	 */
	public DotGenerator(Model ontModel) {
		this.ontModel = ontModel;
	}

	/**
	 * Generates the dot.
	 * @param writer 
	 */
	public void generateDot(Writer writer, String... header) {
		if ( writer instanceof PrintWriter ) {
			this.pw = (PrintWriter) writer;
		}
		else {
			this.pw = new PrintWriter(writer);
		}
		
		
		pw.println("# generated by " +this.getClass().getName()+ " on " +new Date());
		if ( header != null ) {
			for ( String line : header ) {
				if ( ! line.trim().startsWith("#") ) {
					line = "#" + line;
				}
				pw.println(line);
			}
		}
		
		pw.println();

		pw.println("digraph {");
		
//		pw.println("  rankdir=LR\n");

		if ( includeLegend ) {
			_outLegend();
			pw.println(
					"subgraph clusterContent {\n" +
					"  color=white\n"
			);
		}

		
		_outNodeStyles();
		
		_outEdgeStyles();

		pw.println();
		
		List<EntityInfo> entities = _classes();
		
		for ( EntityInfo entityInfo : entities ) {
			String name = entityInfo.getLocalName();
			String label = "{ " +name;
			

			for ( PropValue pv : entityInfo.getProps() ) {
				label += " |{" +pv.getPropName()+ " | " +pv.getValueName()+ " }";
			}
			
			
			label += " }";
			
			pw.println("  \"" +name+ "\"   [ shape=record, label=\"" +label+ "\" ]");	
		}
	
		pw.println();

		_hierarchy(ontModel);
		_objectProperties(ontModel);
		
		
		if ( includeLegend ) {
			pw.println("}");
		}

		
		pw.println("}");
	}
	
	private void _outLegend() {
		pw.println(
				"subgraph clusterLegend {\n" +
				"   node [ label=\"\", shape=point, color=white, fillcolor=white, style=filled, ];\n" +
				"   edge [ fontname=\"helvetica\", fontsize=9, ]\n" +
				"\n" +
				"   _aa -> _bb [ label=\" subClassOf\", dir=back, arrowtail=onormal, arrowsize=1.5, ]\n" +
				"   _bb -> _cc [ label=\" object property\", dir=back, color=darkgreen, fontcolor=darkgreen, arrowtail=vee]\n" +
				"}\n"
		);
	}

	
	private void _outNodeStyles() {
		pw.println("  node [ " +
				"shape=box, " +
				"fillcolor=cornsilk, " +
				"style=filled, " +
				"fontname=\"helvetica\", " +
				"]; "
		);
	}

	
	private void _outEdgeStyles() {
		pw.println("  edge [ " +
				"fontname=\"helvetica\", " +
				"fontsize=11, " +
				"]; "
		);
	}



	private void _outSubclass(String sub, String sup) {
		pw.println("  \"" +sup+ "\" -> \"" +sub+ "\"   [ " +
						"dir=back, " +
						"arrowtail=onormal, " +
						"arrowsize=2.0," +
						" ]"
		);
	}

	private void _outObjectProp(String domain, String prop, String range) {
		pw.println("  \"" +domain+ "\" -> \"" +range+ "\""
				+ "   [ " +
						"label=\"" +prop+ "\", " +
//						"labelfloat=true, " +
//						"decorate=true, " +
						"color=darkgreen, " +
						"fontcolor=darkgreen, " +
						"arrowhead=vee, " +
					"]"
		);	

	}


	
	private List<EntityInfo> _classes() {
		
		List<EntityInfo> entities = new ArrayList<EntityInfo>();
		
		final String CLASSES_QUERY = PREFIXES + 
			"SELECT ?class " +
			"WHERE { ?class rdf:type owl:Class . }"
		;
		Query query = QueryFactory.create(CLASSES_QUERY);
		QueryExecution qe = QueryExecutionFactory.create(query, ontModel);
		
		ResultSet results = qe.execSelect();
		
		while ( results.hasNext() ) {
			QuerySolution sol = results.nextSolution();
			Iterator<?> varNames = sol.varNames();
			while ( varNames.hasNext() ) {
				String varName = String.valueOf(varNames.next());
				RDFNode rdfNode = sol.get(varName);
				
				if ( rdfNode.isAnon() ) {
					continue;
				}
				
				ClassInfo entityInfo = new ClassInfo();
				
				
				if ( rdfNode.isLiteral() ) {
					Literal lit = (Literal) rdfNode;
					String entityName = String.valueOf(lit.getValue());
					entityInfo.setLocalName(entityName);
				}
				else if ( rdfNode.isResource() ) {
					Resource rsr = (Resource) rdfNode;
					
					if ( ! rsr.isAnon() ) {
						
						if ( OWL.Thing.getURI().equals(rsr.getURI()) ) {
							continue;
						}
						
//						String ns = rsr.getNameSpace();
						String localName = rsr.getLocalName();

						entityInfo.setLocalName(localName);

						_addDataProps(rsr.getURI(), entityInfo, ontModel);
					}
				}
				
				if ( entityInfo != null ) {
					entities.add(entityInfo);
				}
			}
		}
		
		return entities;
	}
	
	private void _hierarchy(Model model) {
		String SUBCLASS_QUERY = PREFIXES + 
			"SELECT ?sub ?sup " +
			"WHERE { ?sub rdfs:subClassOf ?sup . }"
		;
		
		Query query = QueryFactory.create(SUBCLASS_QUERY);
		QueryExecution qe = QueryExecutionFactory.create(query, model);
		
		ResultSet results = qe.execSelect();
		
		while ( results.hasNext() ) {
			QuerySolution sol = results.nextSolution();
			
			String sub = null;
			String sup = null;
			
			Iterator<?> varNames = sol.varNames();
			while ( varNames.hasNext() ) {
				String varName = String.valueOf(varNames.next());
				RDFNode rdfNode = sol.get(varName);
				
				if ( rdfNode.isAnon() ) {
					continue;
				}

				String localName;
				
				if ( rdfNode.isLiteral() ) {
					Literal lit = (Literal) rdfNode;
					localName = String.valueOf(lit.getValue());
				}
				else if ( rdfNode.isResource() ) {
					Resource rsr = (Resource) rdfNode;
					
					if ( OWL.Thing.getURI().equals(rsr.getURI()) ) {
						continue;
					}

					localName = rsr.getLocalName();
				}
				else {
					localName = String.valueOf(rdfNode);
				}
				
				if ( varName.equals("sub") ) {
					sub = localName;
				}
				else if ( varName.equals("sup") ) {
					sup = localName;
				}
			}
			
			if ( sub != null && sup != null ) {
				_outSubclass(sub, sup);
			}
		}
	}

	
	private void _objectProperties(Model model) {
		final String PROPERTIES_QUERY = PREFIXES + 
			"SELECT ?domain ?prop ?range " +
			"WHERE { ?prop rdf:type owl:ObjectProperty . " +
			"        ?prop rdfs:domain ?domain ." +
			"        ?prop rdfs:range ?range . " +
			"}"
		;
		
		
		Query query = QueryFactory.create(PROPERTIES_QUERY);
		QueryExecution qe = QueryExecutionFactory.create(query, model);
		
		ResultSet results = qe.execSelect();
		
		while ( results.hasNext() ) {
			QuerySolution sol = results.nextSolution();
			
			String prop = null;
			String domain = null;
			String range = null;
			
			Iterator<?> varNames = sol.varNames();
			while ( varNames.hasNext() ) {
				String varName = String.valueOf(varNames.next());
				RDFNode rdfNode = sol.get(varName);
				
				if ( rdfNode.isAnon() ) {
					continue;
				}

				String localName;
				
				if ( rdfNode.isLiteral() ) {
					Literal lit = (Literal) rdfNode;
					localName = String.valueOf(lit.getValue());
				}
				else if ( rdfNode.isResource() ) {
					Resource rsr = (Resource) rdfNode;
					localName = rsr.getLocalName();
				}
				else {
					localName = String.valueOf(rdfNode);
				}
				
				if ( varName.equals("prop") ) {
					prop = localName;
				}
				else if ( varName.equals("domain") ) {
					domain = localName;
				}
				else if ( varName.equals("range") ) {
					range = localName;
				}
			}
			
			if ( prop != null && domain != null && range != null ) {
				_outObjectProp(domain, prop, range);
			}
		}
		

	}


	private void _addDataProps(String entityUri, EntityInfo entityInfo, Model model) {
		final String DATA_PROPS_QUERY_TEMPLATE = PREFIXES +
			"SELECT ?prop ?range " +
			"WHERE { ?prop rdf:type owl:DatatypeProperty . " +
			"        ?prop rdfs:domain <{E}> ." +
			"        ?prop rdfs:range ?range . " +
			"}"
		;		
		String queryStr = DATA_PROPS_QUERY_TEMPLATE.replaceAll("\\{E\\}", entityUri);
		Query query = QueryFactory.create(queryStr);
		QueryExecution qe = QueryExecutionFactory.create(query, model);
		
		ResultSet results = qe.execSelect();
		
		while ( results.hasNext() ) {
			QuerySolution sol = results.nextSolution();
			Iterator<?> varNames = sol.varNames();
			
			String propName = null, propUri = null;
			String valueName = null, valueUri = null;
			
			while ( varNames.hasNext() ) {
				String varName = varNames.next().toString();
				RDFNode rdfNode = sol.get(varName);
				
				if ( rdfNode.isAnon() ) {
					continue;
				}
				
				if ( varName.equals("prop") ) {
					if ( rdfNode.isResource() ) {
						Resource r = (Resource) rdfNode;
						propName = r.getLocalName();
						propUri = r.getURI();
					}
					else {
						propName = rdfNode.toString();
						
						// if propName looks like a URL, associate the link also:
						try {
							new URL(propName);
							propUri = propName;
						}
						catch (MalformedURLException ignore) {
						}
					}
				}
				else if ( varName.equals("range") ) {
					if ( rdfNode.isResource() ) {
						Resource r = (Resource) rdfNode;
						valueName = r.getLocalName();
						valueUri = r.getURI();
					}
					else {
						valueName = rdfNode.toString();
						// if valueName looks like a URL, associate the link also:
						try {
							new URL(valueName);
							valueUri = valueName;
						}
						catch (MalformedURLException ignore) {
						}
					}
				}
			}
			
			if ( valueName == null ) {
				// TODO  temporarily assign ".." to null value
				// TODO display enumerations?
				valueName = "...";  
			}
			
			PropValue pv = new PropValue(propName, propUri, valueName, valueUri);
			entityInfo.getProps().add(pv);
		}
	}

	
	
}


class EntityInfo implements Serializable {
	private static final long serialVersionUID = 1L;

	// used only on the client side
	private transient char code;
	
	
	private String localName;
	private String displayLabel;
	private String comment;
	
	private List<PropValue> props;
	
	
	public char getCode() {
		return code;
	}
	public void setCode(char code) {
		this.code = code;
	}
	

	public String getLocalName() {
		return localName;
	}
	public void setLocalName(String localName) {
		this.localName = localName;
	}
	public String getDisplayLabel() {
		return displayLabel;
	}
	public void setDisplayLabel(String displayLabel) {
		this.displayLabel = displayLabel;
	}
	public String getComment() {
		return comment;
	}
	public void setComment(String comment) {
		this.comment = comment;
	}
	
	public List<PropValue> getProps() {
		if ( props == null ) {
			props = new ArrayList<PropValue>();
		}
		return props;
		
	}
}



 class PropValue implements Serializable {
	private static final long serialVersionUID = 1L;

	private String propName;
	private String propUri;
	
	private String valueName;
	private String valueUri;
	
	
	// no-arg constructor
	public PropValue() {
	}
	
	
	public PropValue(String propName, String propUri, String valueName, String valueUri) {
		super();
		this.propName = propName;
		this.propUri = propUri;
		this.valueName = valueName;
		this.valueUri = valueUri;
	}


	public String getPropName() {
		return propName;
	}


	public void setPropName(String propName) {
		this.propName = propName;
	}


	public String getPropUri() {
		return propUri;
	}


	public void setPropUri(String propUri) {
		this.propUri = propUri;
	}


	public String getValueName() {
		return valueName;
	}


	public void setValueName(String valueName) {
		this.valueName = valueName;
	}


	public String getValueUri() {
		return valueUri;
	}


	public void setValueUri(String valueUri) {
		this.valueUri = valueUri;
	}
}



 class OntologyInfo implements Serializable {
	private static final long serialVersionUID = 1L;

	// used only on the client side
	private transient char code;
	
	private String uri;
	private String displayLabel;
	
	private List<EntityInfo> entities;
	

	
	public char getCode() {
		return code;
	}
	public void setCode(char code) {
		this.code = code;
	}
	
	public String getUri() {
		return uri;
	}
	public void setUri(String uri) {
		this.uri = uri;
	}
	public String getDisplayLabel() {
		return displayLabel;
	}
	public void setDisplayLabel(String displayLabel) {
		this.displayLabel = displayLabel;
	}
	public List<EntityInfo> getEntities() {
		return entities;
	}
	public void setEntities(List<EntityInfo> entities) {
		this.entities = entities;
	}
	
	
	public boolean equals(Object other) {
		return other instanceof OntologyInfo && uri.equals(((OntologyInfo) other).uri);
	}
	public int hashCode() {
		return uri.hashCode();
	}
	
}


 class ClassInfo extends EntityInfo implements Serializable {
	 private static final long serialVersionUID = 1L;

 }

