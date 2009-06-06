package org.mmisw.ontmd.gwt.client.portal;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mmisw.iserver.gwt.client.rpc.BaseOntologyData;
import org.mmisw.iserver.gwt.client.rpc.EntityInfo;
import org.mmisw.iserver.gwt.client.rpc.OntologyData;
import org.mmisw.iserver.gwt.client.rpc.OtherDataCreationInfo;
import org.mmisw.iserver.gwt.client.rpc.OtherOntologyData;
import org.mmisw.iserver.gwt.client.rpc.PropValue;
import org.mmisw.iserver.gwt.client.rpc.TempOntologyInfo;
import org.mmisw.ontmd.gwt.client.Main;
import org.mmisw.ontmd.gwt.client.util.IRow;
import org.mmisw.ontmd.gwt.client.util.UtilTable;

import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;


/**
 * Panel for showing/capturing the contents of an "other" ontology, meaning
 * one not handled with voc2rdf or Vine.
 * 
 * 
 * @author Carlos Rueda
 */
public class OtherOntologyContentsPanel extends BaseOntologyContentsPanel {

	private UploadLocalOntologyPanel uploadLocalOntologyPanel;
	private Widget contents;
	private final VerticalPanel widget = new VerticalPanel();
	
	private TempOntologyInfo tempOntologyInfo;
	
	/** the "client" listener, which will be notified after my own listener below */
	private TempOntologyInfoListener tempOntologyInfoListener;
	
	
	private TempOntologyInfoListener myTempOntologyInfoListener;
	
	
	public OtherOntologyContentsPanel(OtherOntologyData ontologyData, 
			TempOntologyInfoListener tempOntologyInfoListener,
			boolean readOnly
	) {
		super(readOnly);
		this.tempOntologyInfoListener = tempOntologyInfoListener;
		
		this.myTempOntologyInfoListener = new TempOntologyInfoListener() {
			public void tempOntologyInfoObtained(TempOntologyInfo tempOntologyInfo) {
				// update my own data and then notify the client listener
				_tempOntologyInfoObtained(tempOntologyInfo);
			}
		};
		
		
		BaseOntologyData baseData = ontologyData.getBaseOntologyData();
		if ( baseData != null ) {
			contents = _prepareOtherWidgetForExistingBaseData(ontologyData);
		}

		_updateInterface();
	}
	
	private void _tempOntologyInfoObtained(TempOntologyInfo tempOntologyInfo) {
		
		Main.log("OtherOntologyContentsPanel: _tempOntologyInfoObtained: " +tempOntologyInfo);
		
		this.tempOntologyInfo = tempOntologyInfo;
		
		OntologyData ontologyData = tempOntologyInfo.getOntologyData();
		BaseOntologyData baseData = ontologyData.getBaseOntologyData();
		if ( baseData != null ) {
			contents = _prepareOtherWidgetForExistingBaseData(ontologyData);
		}

		_updateInterface();


		// notify client:
		if ( tempOntologyInfoListener != null ) {
			tempOntologyInfoListener.tempOntologyInfoObtained(tempOntologyInfo);
		}
		
	}
	
	private void _updateInterface() {
		widget.clear();
		
		if ( !isReadOnly() ) {
			if ( uploadLocalOntologyPanel == null ) {
				uploadLocalOntologyPanel = new UploadLocalOntologyPanel(myTempOntologyInfoListener, true);
			}
			widget.add(uploadLocalOntologyPanel);
		}
		
		if ( contents != null ) {
			widget.add(contents);
		}
	}
	
	
	public void setReadOnly(boolean readOnly) {
		Main.log("OtherOntologyContentsPanel setReadOnly");
		super.setReadOnly(readOnly);
		
		_updateInterface();
	}

	
	

	@Override
	public OtherDataCreationInfo getCreateOntologyInfo() {
		OtherDataCreationInfo odci = new OtherDataCreationInfo();

		// TODO
		Main.log("TODO OtherOntologyContentsPanel create OtherDataCreationInfo");
		odci.setTempOntologyInfo(tempOntologyInfo);
		
		return odci;
	}

	@Override
	public String checkData() {
		if ( tempOntologyInfo == null ) {
			return "No ontology has been uploaded into working space yet";
		}
		if ( tempOntologyInfo.getError() != null ) {
			return "There was a previous error with the uploading of the file (" +tempOntologyInfo.getError()+ ")";
		}
		return null;
	}

	@Override
	public Widget getWidget() {
		return widget;
	}
	
	//
	// TODO use IncrementalCommand's
	//
	@SuppressWarnings("unchecked")
	private Widget _prepareOtherWidgetForExistingBaseData(OntologyData ontologyData) {
		BaseOntologyData baseData = ontologyData.getBaseOntologyData();
		
		VerticalPanel vp = new VerticalPanel();
		vp.setSpacing(4);
		
		Object[] entityGroups = {  
				"Classes", baseData.getClasses(),
				"Properties", baseData.getProperties(),
				"Individuals", baseData.getIndividuals(),
		};

		for (int i = 0; i < entityGroups.length; i += 2) {
			String title = entityGroups[i].toString();
			List<?extends EntityInfo> entities = (List<?extends EntityInfo>) entityGroups[i + 1];
			
			title += " (" +entities.size()+ ")";
			
			DisclosurePanel disclosure = new DisclosurePanel(title);
			disclosure.setAnimationEnabled(true);
			
			Widget entsWidget = _createOtherWidgetForEntities(ontologyData, entities);
			
			disclosure.setContent(entsWidget);
			
			vp.add(disclosure);
			
		}
		
		return vp;
	}


	private Widget _createOtherWidgetForEntities(OntologyData ontologyData, 
			List<? extends EntityInfo> entities) {

		
		if ( entities.size() == 0 ) {
			return new HTML();
		}
		
		Set<String> header = new HashSet<String>();
		
		for ( EntityInfo entity : entities ) {
			List<PropValue> props = entity.getProps();
			for ( PropValue pv : props ) {
				header.add(pv.getPropName());
			}
		}
		
		List<String> colNames = new ArrayList<String>();
		colNames.addAll(header);
		colNames.add(0, "Name");

		UtilTable utilTable = new UtilTable(colNames);
		List<IRow> rows = new ArrayList<IRow>();
		for ( EntityInfo entity : entities ) {
			final Map<String, String> vals = new HashMap<String, String>();
			List<PropValue> props = entity.getProps();
			for ( PropValue pv : props ) {
				vals.put(pv.getPropName(), pv.getValueName());
			}

			vals.put("Name", entity.getLocalName());
			
			rows.add(new IRow() {
				public String getColValue(String sortColumn) {
					return vals.get(sortColumn);
				}
			});
		}
		utilTable.setRows(rows);
		
		return utilTable.getWidget();
	}


}