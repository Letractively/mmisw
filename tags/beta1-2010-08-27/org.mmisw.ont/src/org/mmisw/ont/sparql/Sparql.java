package org.mmisw.ont.sparql;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;

import org.mmisw.ont.JenaUtil2;
import org.mmisw.ont.util.Unfinished;
import org.mmisw.ont.util.Util;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFormatter;
import com.hp.hpl.jena.rdf.model.Model;

/**
 * Dispatcher of SPARQL queries.
 * 
 * @author Carlos Rueda
 */
@Unfinished(priority=Unfinished.Priority.MEDIUM)
public class Sparql {
	
	/**
	 * Executes a SPARQL query against the given model.
	 * 
	 * @param model
	 * @param sparqlQuery
	 * @param form Only used for a "select" query.
	 * @return
	 * @throws Exception 
	 */
	public static QueryResult executeQuery(Model model, String sparqlQuery, String form) throws Exception {
		QueryResult queryResult = new QueryResult();

		Query query;
		QueryExecution qe;
		
		try {
			query = QueryFactory.create(sparqlQuery);
			qe = QueryExecutionFactory.create(query, model);
		}
		catch ( Throwable thr ) {
			String error = "Error preparing query.";
			throw new Exception(error, thr);
		}
		
		try {
			// CONSTRUCT or DESCRIBE
			if ( query.isConstructType() || query.isDescribeType() ) {
				Model model_;
				
				if ( query.isConstructType() ) {
					model_ = qe.execConstruct();
				}
				else {
					// DESCRIBE
					model_ = qe.execDescribe();
				}
				queryResult.setIsEmpty(model_.isEmpty());
				
				JenaUtil2.removeUnusedNsPrefixes(model_);
				
				String str;	
				
				if ( form == null || form.equalsIgnoreCase("owl") || form.equalsIgnoreCase("rdf") ) {
					str = JenaUtil2.getOntModelAsString(model_, "RDF/XML-ABBREV");
					queryResult.setContentType("Application/rdf+xml");
				}
				else if ( form.equalsIgnoreCase("n3") ) {
					str = JenaUtil2.getOntModelAsString(model_, "N3");
					queryResult.setContentType("text/plain");
				}
				else if ( form.equalsIgnoreCase("nt") ) {
					str = JenaUtil2.getOntModelAsString(model_, "N-TRIPLE");
					queryResult.setContentType("text/plain");
				}
				else if ( form.equalsIgnoreCase("ttl") ) {
					str = JenaUtil2.getOntModelAsString(model_, "TURTLE");
					queryResult.setContentType("text/plain");
				}
				else {
					str = JenaUtil2.getOntModelAsString(model_, "RDF/XML-ABBREV");
					queryResult.setContentType("Application/rdf+xml");
				}
				queryResult.setResult(str);
			}
			
			// SELECT
			else if ( query.isSelectType() ) {
				ResultSet results = qe.execSelect();
				queryResult.setIsEmpty(! results.hasNext());
				
				if ( form == null || form.startsWith("html") ) {
					queryResult.setContentType("text/html");
					queryResult.setResult(_htmlSelectResults(results));
				}
				else if ( form.equalsIgnoreCase("csv") ) {
					queryResult.setContentType("text/plain");
					queryResult.setResult(_csvSelectResults(results));
				}
				else if ( form.equalsIgnoreCase("json") ) {
					queryResult.setContentType("application/json");
					queryResult.setResult(_jsonSelectResults(results));
				}
				else {
					queryResult.setContentType("text/plain");
					ByteArrayOutputStream os = new ByteArrayOutputStream();
					ResultSetFormatter.out(os, results, query);
					queryResult.setResult(os.toString());
				}
			}
			
			
			// TODO handle other types of queries.
			else {
				queryResult.setResult("Sorry, query type " +query.getQueryType()+ " not handled yet");
				queryResult.setContentType("text/plain");
			}
		}
		finally {
			qe.close();
		}
		
		return queryResult;
	}

	/** Formats the results in HTML */
	private static String _htmlSelectResults(ResultSet results) {
		
		StringWriter sw = new StringWriter();
		PrintWriter out = new PrintWriter(sw);
		
		out.printf("<table class=\"inline\">%n");
		out.printf("<tr>%n");
		List<?> vars = results.getResultVars();
		for ( Object var: vars ) {
			out.printf("\t<th>%s</th>%n", Util.toHtml(var.toString()));
		}
		out.printf("</tr>%n");
		
		if ( results.hasNext() ) {
			while ( results.hasNext() ) {
				out.printf("<tr>%n");
				QuerySolution sol = results.nextSolution();
				Iterator<?> varNames = sol.varNames();
				while ( varNames.hasNext() ) {
					String varName = String.valueOf(varNames.next());
					String varValue = String.valueOf(sol.get(varName));
					
					String link = Util.getLink(varValue);
					if ( link != null ) {
						out.printf("\t<td><a href=\"%s\">%s</a></td>%n", link, Util.toHtml(varValue));
					}
					else {
						out.printf("\t<td>%s</td>%n", Util.toHtml(varValue));
					}
				}
				out.printf("</tr>%n");
			}
		}
		out.printf("</table>%n");
		
		return sw.toString();
	}
	
	/** Formats the results in JSON */
	private static String _jsonSelectResults(ResultSet results) {
		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ResultSetFormatter.outputAsJSON(bos, results);
		
		return bos.toString();
	}	
	
	/** Formats the results in CSV */
	private static String _csvSelectResults(ResultSet results) {
		
		StringWriter sw = new StringWriter();
		PrintWriter out = new PrintWriter(sw);
		
		// header line:
		String comma = "";
		List<?> vars = results.getResultVars();
		for ( Object var: vars ) {
			String value = var.toString();
			if ( value.indexOf(',') >= 0 ) {
				value = "\"" +value+ "\"";
			}
			out.printf("%s%s", comma, value);
			comma = ",";
		}
		out.printf("%n");

		// contents:
		while ( results.hasNext() ) {
			QuerySolution sol = results.nextSolution();
			comma = "";
			Iterator<?> varNames = sol.varNames();
			while ( varNames.hasNext() ) {
				String varName = String.valueOf(varNames.next());
				String value = String.valueOf(sol.get(varName));
				if ( value.indexOf(',') >= 0 ) {
					value = "\"" +value+ "\"";
				}
				out.printf("%s%s", comma, value);
				comma = ",";
			}
			out.printf("%n");
		}
		
		return sw.toString();
	}

	private Sparql() {}
}