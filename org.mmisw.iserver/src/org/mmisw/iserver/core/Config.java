package org.mmisw.iserver.core;


/**
 * Provides the configuration parameter values.
 * 
 * @author Carlos Rueda
 */
public class Config {
	
	/**
	 * The configuration properties used by the service.
	 */
	public enum Prop {
		
		VERSION                       ("iserver.app.version",  "1.5.0.beta0"),
		BUILD                         ("iserver.app.build",    "buildPend"),
		;
		
		private final String name;
		private String value;

		Prop(String name, String value) { 
			this.name = name;
			this.value = value;
		};
		
		public String getName() { return name; }

		public String getValue() { return value; }
		
//		private void setValue(String value) { this.value = value; }
	}

	
	private static final Config instance = new Config();
	
	/** 
	 * Gets the unique instance of this class.
	 */
	public static Config getInstance() {
		return instance;
	}
	
	
	private Config() {};

}