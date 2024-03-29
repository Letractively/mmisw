package org.mmisw.watchdog.onts.cf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.IOUtils;
import org.mmisw.watchdog.Watchdog.BaseProgram;
import org.mmisw.watchdog.conversion.IConverter;
import org.mmisw.watchdog.onts.cf.jena.CfConverterJena;
import org.mmisw.watchdog.onts.cf.skosapi.CfConverterSkosApi;
import org.mmisw.watchdog.orr.RegisterOntology;
import org.mmisw.watchdog.orr.RegisterOntology.RegistrationResult;
import org.mmisw.watchdog.util.WdConstants;

/**
 * Main program for CF conversion.
 * 
 * @author Carlos Rueda
 */
public class Cf extends BaseProgram {
	
	/** Default input URI */
	private static final String DEFAULT_INPUT = 
		"http://cf-pcmdi.llnl.gov/documents/cf-standard-names/standard-name-table/current/cf-standard-name-table.xml";
	
	/** Message indicating default output filename  */
	private static final String DEFAULT_OUTPUT_MSG = "${basename}-${version_number}-${impl}.owl (under ${workspace})";
	
	/** Default namespace for resulting ontology */
	private static final String DEFAULT_NAMESPACE = "http://mmisw.org/ont/cf/parameter/";
	
	/** Default implementation code */
	private static final String DEFAULT_IMPL = WdConstants.JENA_IMPL;

	private static final boolean DEFAULT_FORCE = false;

	
	/////////////////////////
	// registration parameters
	
	private static final String ORR_DEFAULT_FORM_ACTION = "http://mmisw.org/orr/direg";
	

	
	/**
	 * Main program for CF conversion.
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws Exception {
		new Cf().run(args);
	}
	
	/** Never returns */
	protected void _usage(String msg) {
		if ( msg == null ) {
			System.out.println(
					"USAGE: " +getClass().getName()+ " --ws <directory> [options]\n" +
					"  --ws <directory>        workspace directory (required)\n" +
					"  options: (default value in parenthesis)\n" +
					"    --input <url>         (" +DEFAULT_INPUT+ ")\n" +
					"    --ns <uri>            (" +DEFAULT_NAMESPACE+ ")\n" +
					"    --output <filename>   (" +DEFAULT_OUTPUT_MSG+ ")\n" +
					"    --force               (" +DEFAULT_FORCE+ ")\n" +
					"    --impl [jena|skosapi] (" +DEFAULT_IMPL+ ")\n" +
					"   for registration:\n" +
					"    --username <username> (" +WdConstants.ORR_DEFAULT_USERNAME+ ")\n" +
					"    --password <password> \n" +
					"    --formAction <action> (" +ORR_DEFAULT_FORM_ACTION+ ")\n" +
					"");
			System.exit(0);
		}
		else {
			System.err.println("Error: " +msg);
			System.err.println("Try " +getClass().getName()+ " --help\n");
			System.exit(1);
		}
	}

	public void run(String[] args) throws Exception {
		if ( args.length == 0 || args[0].matches(".*help") ) {
			_usage(null);
		}
		
		String workspace = null;
		String input = DEFAULT_INPUT;
		String output = null;
		boolean force = DEFAULT_FORCE;
		String namespace = DEFAULT_NAMESPACE;
		String impl = DEFAULT_IMPL;
		
		// ORR registration
		String orrUsername = WdConstants.ORR_DEFAULT_USERNAME;
		String orrPassword = null;
		String orrFormAction = ORR_DEFAULT_FORM_ACTION;
		
	
		int arg = 0;
		for ( ; arg < args.length && args[arg].startsWith("--"); arg++ ) {
			if ( args[arg].equals("--ws") ) {
				workspace = args[++arg]; 
			}
			else if ( args[arg].equals("--input") ) {
				input = args[++arg]; 
			}
			else if ( args[arg].equals("--ns") ) {
				namespace = args[++arg]; 
			}
			else if ( args[arg].equals("--output") ) {
				output = args[++arg]; 
			}
			else if ( args[arg].equals("--force") ) {
				force = true;
			}
			else if ( args[arg].equals("--impl") ) {
				impl = args[++arg]; 
			}
			
			// ORR
			else if ( args[arg].equals("--username") ) {
				orrUsername = args[++arg]; 
			}
			else if ( args[arg].equals("--password") ) {
				orrPassword = args[++arg]; 
			}
			else if ( args[arg].equals("--formAction") ) {
				orrFormAction = args[++arg]; 
			}
			else {
				_usage("unrecognized parameter: " +args[arg]);
			}
		}
		if ( arg < args.length ) {
			String uargs = "";
			for ( ; arg< args.length; arg++ ) {
				uargs += args[arg] + " ";
			}
			_usage("Unexpected arguments: " +uargs);
		}
		if ( workspace == null ) {
			_usage("Missing required --ws parameter");
		}
		
		URL inputUrl = new URL(input);
		
		String inputContents = _getInputContents(inputUrl);
		
		File workspaceDir = _prepareWorkspace(workspace);
		
		namespace = _prepareNamespace(namespace);
		IConverter creator = _prepareCreator(impl);
		
		Map<String, String> props = _convert(creator, inputContents, namespace);
		
		_reportProps(props);
		
		String version_number = props.get("version_number");
		File outputFile = _writeOutputs(
				creator, workspaceDir, inputUrl, inputContents, 
				version_number, impl, output, force);
		
		if ( outputFile == null ) {
			// outputs not written. Nothing else to do:
			return;
		}
		
		// ORR registration:
		if ( orrPassword != null ) {
			_registerOntology(orrUsername, orrPassword, orrFormAction, namespace, outputFile);
		}
		else {
			_log("Skipping registration (indicate at least --password to perform registration)");
		}
	}

	private IConverter _prepareCreator(String impl) {
		if ( impl.equalsIgnoreCase(WdConstants.JENA_IMPL)) {
			return new CfConverterJena();
		}
		else if ( impl.equalsIgnoreCase(WdConstants.SKOSAPI_IMPL)) {
			return new CfConverterSkosApi();
		}
		else {
			_usage("No implementation available for " +impl);
		}
		return null;
	}

	/**
	 * Does the conversion using the given creator object.
	 */
	private Map<String, String> _convert(
			IConverter creator, 
			String inputContents, 
			String namespace
	) throws Exception {
		
		creator.setInput(inputContents);
		creator.setNamespace(namespace);
		Map<String, String> props = creator.convert();
		return props;
	}
	
	/**
	 * Writes the outputs. Returns the output file iff outputs effectively written.
	 */
	private File _writeOutputs(IConverter creator, File workspaceDir, 
			URL inputUrl, String inputContents, String version_number, String impl,
			String output, boolean force
	) throws Exception {
		
		String[] nx = _getFilenameAndExtension(inputUrl);
		String downloadName = nx[0] + "-" +version_number + nx[1];
		File downloadFile = new File(workspaceDir, downloadName);
		
		if ( downloadFile.exists() ) {
			if ( force ) {
				_log("Overwriting " +downloadFile);
			}
			else {
				_log(downloadFile+ " already exists.");
				_log("Exiting. No output written.");
				return null;
			}
		}
		
		// save downloaded file:
		IOUtils.write(inputContents, new FileOutputStream(downloadFile), "UTF-8");
		_log(downloadFile+ ": input saved");
		
		// save result of conversion:
		if ( output == null ) {
			output = nx[0] + "-" +version_number+ "-" +impl+ ".owl";
		}
		File outputFile = new File(workspaceDir, output);
		
		creator.save(outputFile.toString());
		_log(outputFile+ ": resulting ontology saved");
		
		return outputFile;
	}

	
	/**
	 * Prepares and requests the registration of the ontology.
	 */
	private void _registerOntology(
			String username, String password,
			String formAction, String namespace, File outputFile
	) throws Exception{
		
		_log("Preparing registration of " +outputFile+ " ...");
		
		String fileContents = IOUtils.toString(new FileInputStream(outputFile), "UTF-8");

		// remove trailing separator:
		String ontologyUri = namespace.replaceAll("(/|#)+$", "");
		
		String graphId = "";
		RegistrationResult result = RegisterOntology.register(
				username, password, ontologyUri , outputFile.toString(), fileContents, graphId, formAction);
		
		_log("Response status: " +result.status+ ": " +HttpStatus.getStatusText(result.status));
		_log("Response body:\n" +result.message);
	}

}
