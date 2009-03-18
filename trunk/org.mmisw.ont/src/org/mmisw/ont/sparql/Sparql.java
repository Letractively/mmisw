package org.mmisw.ont.sparql;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;

import org.mmisw.ont.JenaUtil2;
import org.mmisw.ont.MmiUri;
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
	
	public static class QueryResult {
		private String result;
		private String contentType;

		void setResult(String result) {
			this.result = result;
		}

		void setContentType(String contentType) {
			this.contentType = contentType;
		}
		public String getResult() {
			return result;
		}

		public String getContentType() {
			return contentType;
		}
	}

	/**
	 * Executes a SPARQL query against the given model.
	 * 
	 * @param model
	 * @param sparqlQuery
	 * @param form Only used for a "select" query.
	 * @return
	 */
	public static QueryResult executeQuery(Model model, String sparqlQuery, String form) {
		QueryResult queryResult = new QueryResult();
		
		Query query = QueryFactory.create(sparqlQuery);
		QueryExecution qe = QueryExecutionFactory.create(query, model);
		
		try {
			if ( query.isConstructType() ) {
				Model model_ = qe.execConstruct();
				
				JenaUtil2.removeUnusedNsPrefixes(model_);
				
				String str = JenaUtil2.getOntModelAsString(model_, "RDF/XML-ABBREV");				
				queryResult.setResult(str);
				queryResult.setContentType("Application/rdf+xml");
			}
			else if ( query.isSelectType() ) {
				ResultSet results = qe.execSelect();
				
				if ( form == null || form.equalsIgnoreCase("html") ) {
					queryResult.setContentType("text/html");
					queryResult.setResult(_htmlSelectResults(results));
				}
				else if ( form.equalsIgnoreCase("csv") ) {
					queryResult.setContentType("text/plain");
					queryResult.setResult(_csvSelectResults(results));
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
					String varName = varNames.next().toString();
					String varValue = sol.get(varName).toString();
					
					String link = getLink(varValue);
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
	
	/** 
	 * Returns a string that can be used as a link. 
	 * If it is an MmiUri, a ".html" is appended;
	 * otherwise, if it is a valid URL, it is returned as it is;
	 * otherwise, null is returned.
	 * 
	 * @param value a potential URL
	 * @return the string that can be used as a link as stated above; null if value is not a URL.
	 */
	private static String getLink(String value) {
		// try mmiUri:
		try {
			MmiUri mmiUri = new MmiUri(value);
			return mmiUri.getTermUri() + ".html";
		}
		catch (URISyntaxException e1) {
			// ignore. Try URL below.
		}
		
		// try regular URL:
		try {
			URL url = new URL(value);
			return url.toString();
		}
		catch (MalformedURLException e) {
			// ignore.
		}
		
		return null;
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
				String varName = varNames.next().toString();
				String value = sol.get(varName).toString();
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