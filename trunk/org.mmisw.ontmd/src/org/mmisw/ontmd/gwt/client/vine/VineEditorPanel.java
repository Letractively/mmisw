package org.mmisw.ontmd.gwt.client.vine;

import java.util.List;
import java.util.Set;

import org.mmisw.iserver.gwt.client.rpc.MappingOntologyData;
import org.mmisw.iserver.gwt.client.rpc.OntologyData;
import org.mmisw.iserver.gwt.client.rpc.RegisteredOntologyInfo;
import org.mmisw.iserver.gwt.client.rpc.vine.Mapping;
import org.mmisw.ontmd.gwt.client.Main;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.DecoratorPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;

/**
 * The main editor panel. This contains the ontology selection panel and
 * the multi page editor.
 * 
 * @author Carlos Rueda
 */
public class VineEditorPanel extends VerticalPanel {
	
	private MappingOntologyData ontologyData;
	private boolean readOnly;
	private VerticalPanel layout;
	
	private OntologySelection ontSel;
	private MapperPage mapperPage;
	private MappingsPanel mappingsPanel;
	
	
	public VineEditorPanel(MappingOntologyData ontologyData, boolean readOnly) {
		super();
		this.ontologyData = ontologyData;
		this.readOnly = readOnly;
		
		layout = new VerticalPanel();
		DecoratorPanel decPanel = new DecoratorPanel();
	    decPanel.setWidget(layout);
	    add(decPanel);

	    _setUp();
	}
	
	private void _setUp() {
		layout.clear();
		
		Set<String> namespaces = ontologyData.getNamespaces();
		
		VineMain.setWorkingUrisWithGivenNamespaces(namespaces);
		
		List<Mapping> mappings = ontologyData.getMappings();

		if ( ! readOnly || VineMain.getWorkingUris().size() > 0 ) {
			ontSel = new OntologySelection(this);
			layout.add(ontSel);
			layout.setCellHorizontalAlignment(ontSel, ALIGN_CENTER);
		}
		
		mappingsPanel = new MappingsPanel(readOnly);
		mappingsPanel.setMappings(mappings);
		
		
		if ( ! readOnly ) {
			mapperPage = new MapperPage(mappingsPanel);
			layout.add(mapperPage);
		}

	    layout.add(mappingsPanel);

	    // prepare command to load data of working ontologies
	    // (in particular, this will enable the search)
		DeferredCommand.addCommand(new Command() {
			public void execute() {
				_loadDataOfWorkingOntologiesForMapping(0);
			}
		});

	}

	public List<Mapping> getMappings() {
		return mappingsPanel.getMappings();
	}
	
	public boolean isReadOnly() {
		return readOnly;
	}

	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
		_setUp();
	}



	/**
	 * Gets the entities and then notifies the event to dependent components.
	 * @param ontologySelection 
	 * @param ontologyInfo
	 */
	void notifyWorkingOntologyAdded(final OntologySelection ontologySelection, RegisteredOntologyInfo ontologyInfo, final MyDialog popup) {
		
		HorizontalPanel hp = new HorizontalPanel();
		hp.setSpacing(10);
		
		// TODO: why preloaded animated images don't animate? ...
		// (see http://groups.google.com/group/Google-Web-Toolkit-Contributors/browse_thread/thread/c6bc51da338262af)
//		hp.add(Main.images.loading().createImage());
		hp.add(new HTML(
			// ... workaround: insert it with img tag -- which does work, but that's not the idea
			"<img src=\"" +GWT.getModuleBaseURL()+ "images/loading.gif\">" +
			" Loading " +ontologyInfo.getUri()+ 
			" : <i>" +ontologyInfo.getDisplayLabel()+ "</i>" +
			"<br/>Please wait..."
		));
		popup.setWidget(hp);
		popup.setText("Loading vocabulary...");
		
		AsyncCallback<RegisteredOntologyInfo> callback = new AsyncCallback<RegisteredOntologyInfo>() {
			public void onFailure(Throwable thr) {
				RootPanel.get().add(new HTML(thr.toString()));
				popup.hide();
			}

			public void onSuccess(RegisteredOntologyInfo ontologyInfo) {
				popup.setWidget(new HTML("Load complete"));
				
				Main.log("getOntologyContents: " +ontologyInfo.getUri()+ " completed.");
				
				if ( ontologyInfo.getOntologyData() == null ) {
					Main.log("getOntologyContents: unexpected: data not retrieved");
				}
				
				VineMain.ontologySucessfullyLoaded(ontologyInfo);
				
				VineMain.addWorkingUri(ontologyInfo.getUri());
				ontologySelection.refreshListWorkingUris();
				
				if ( mapperPage != null ) {
					mapperPage.notifyWorkingOntologyAdded(ontologyInfo);
				}
				
				popup.hide();
			}
		};
		
		Main.log("getOntologyContents: " +ontologyInfo.getUri()+ " starting");
		Main.ontmdService.getOntologyContents(ontologyInfo, null, callback);
	}

	
	/**
	 * Loads the data for the working ontologies that do not have data yet.
	 * This is a recursive routine used to traverse the list of working
	 * ontologies with a RPC call for each entry needing the retrieval of data.
	 * 
	 * @param currentIdx the current index to examine.
	 */
	private void _loadDataOfWorkingOntologiesForMapping(final int currentIdx) {
		List<String> uris = VineMain.getWorkingUris();
		if ( uris.size() == 0 || currentIdx >= uris.size() ) {
			// we re done.
			if ( currentIdx > 0 ) {
				// if we did something, refresh the OntologySelection:
				if ( ontSel != null ) {
					ontSel.refreshListWorkingUris();
				}
			}
			return;
		}
	
		final String log_prefix = "_loadDataOfWorkingOntologiesForMapping(" +currentIdx+ "): ";
		
		String uri = uris.get(currentIdx);
		RegisteredOntologyInfo ontologyInfo = VineMain.getRegisteredOntologyInfo(uri);
		
		if ( ontologyInfo == null ) {
			// Not a registered ontology; continue to next entry:
			_loadDataOfWorkingOntologiesForMapping(currentIdx + 1);
			return;
		}

		if ( ontologyInfo.getError() != null ) {
			// continue to next entry:
			_loadDataOfWorkingOntologiesForMapping(currentIdx + 1);
			return;
		}

		OntologyData ontologyData = ontologyInfo.getOntologyData();
		if ( ontologyData != null ) {
			// this entry already has data; continue to next entry:
			_loadDataOfWorkingOntologiesForMapping(currentIdx + 1);
			return;
		}
		
		// this entry needs data.
		
		Main.log(log_prefix +ontologyInfo.getUri()+ " starting");
		AsyncCallback<RegisteredOntologyInfo> callback = new AsyncCallback<RegisteredOntologyInfo>() {

			public void onFailure(Throwable thr) {
				String error = thr.getClass().getName()+ ": " +thr.getMessage();
				while ( (thr = thr.getCause()) != null ) {
					error += "\ncaused by: " +thr.getClass().getName()+ ": " +thr.getMessage();
				}
				Main.log(log_prefix + " ERROR: " +error);
				Window.alert(error);
			}

			public void onSuccess(RegisteredOntologyInfo ontologyInfo) {
				Main.log(log_prefix +ontologyInfo.getUri()+ " completed.");
				
				if ( ontologyInfo.getOntologyData() == null ) {
					Main.log("  UNEXPECTED: data not retrieved");
				}
				
				VineMain.ontologySucessfullyLoaded(ontologyInfo);
				VineMain.addWorkingUri(ontologyInfo.getUri());
				
				if ( mapperPage != null ) {
					mapperPage.notifyWorkingOntologyAdded(ontologyInfo);
				}
				
				// continue with next entry:
				DeferredCommand.addCommand(new Command() {
					public void execute() {
						_loadDataOfWorkingOntologiesForMapping(currentIdx + 1);
					}
				});

			}
			
		};
		Main.ontmdService.getOntologyContents(ontologyInfo, null, callback );


	}


}
