package org.mmisw.ontmd.gwt.client.voc2rdf;

import java.util.Map;

import org.mmisw.ontmd.gwt.client.Main;
import org.mmisw.ontmd.gwt.client.rpc.LoginResult;
import org.mmisw.ontmd.gwt.client.voc2rdf.rpc.ConversionResult;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.CellPanel;
import com.google.gwt.user.client.ui.DecoratorPanel;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.TabPanel;
import com.google.gwt.user.client.ui.VerticalPanel;

/**
 * The main panel.
 * 
 * @author Carlos Rueda
 */
public class Voc2RdfMainPanel extends VerticalPanel {


	private static final String INFO = 				
		"Voc2RDF transforms a vocabulary in text format into a format that greatly " +
		"facilitates the exploitation of your vocabulary by Semantic Web tools, thus " +
		"enabling semantic interoperability. " +
		"This format is called the <a href=\"http://www.w3.org/TR/REC-rdf-syntax/\"" +
		" target=\"_blank\" >Resource Description Framework.</a> " 	
		;
	
	private CellPanel container = new VerticalPanel();
	private TabPanel tabPanel = new TabPanel();
	
	private VocabPanel vocabPanel = new VocabPanel(this);
	private ConversionPanel conversionPanel = new ConversionPanel(this);
	
	private LoginResult loginResult;
	
	
	Voc2RdfMainPanel(final Map<String, String> params) {
		super();
		
		///////////////////////////////////////////////////////////////////////////
		// conveniences for testing in development environment
		if ( ! GWT.isScript() ) {
			
			if ( true ) {    // true for auto-login
				loginResult = new LoginResult();
				loginResult.setSessionId("22222222222222222");
				loginResult.setUserId("1002");
				loginResult.setUserName("carueda");
			}
		}
		
		
	    if ( loginResult == null && params.get("sessionId") != null && params.get("userId") != null ) {
	    	loginResult = new LoginResult();
	    	loginResult.setSessionId(params.get("sessionId"));
	    	loginResult.setUserId(params.get("userId"));
	    }

		
		
		add(Main.images.voc2rdf2().createImage());
		
//		container.setSize("800px", "450px");
		DecoratorPanel decPanel = new DecoratorPanel();
	    decPanel.setWidget(container);
	    add(decPanel);

	    
	    /////////
		
		
		FlexTable flexPanel = new FlexTable();
		flexPanel.setWidth("800px");
		int row = 0;
		
		HTML info = new HTML(INFO);
		VerticalPanel infoPanel = new VerticalPanel();
		infoPanel.setSpacing(5);
		infoPanel.add(info);
		flexPanel.getFlexCellFormatter().setColSpan(row, 0, 3);
		flexPanel.setWidget(row, 0, infoPanel);
		flexPanel.getCellFormatter().setAlignment(row, 0, ALIGN_LEFT, ALIGN_MIDDLE);
		row++;

		
		flexPanel.getFlexCellFormatter().setColSpan(row, 0, 3);
		flexPanel.setWidget(row, 0, tabPanel);
		tabPanel.add(vocabPanel, "Vocabulary");
		tabPanel.add(conversionPanel, "Conversion");
		tabPanel.selectTab(0);
	    
	    container.add(flexPanel); // tabPanel);
	}
	
	
	void converting() {
		conversionPanel.showProgressMessage("Converting. Please wait ...");
		conversionPanel.updateForm(null, null);
	}
	
	void conversionError(String error) {
		conversionPanel.setText(error);
		conversionPanel.updateForm(null, null);
	}
	
	void conversionOk(ConversionResult conversionResult) {
		tabPanel.selectTab(tabPanel.getWidgetIndex(conversionPanel));
		conversionPanel.setText(conversionResult.getRdf());
		conversionPanel.updateForm(conversionResult.getPathOnServer(), loginResult);
	}

}
