package org.mmisw.ontmd.gwt.client.portal;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.mmisw.iserver.gwt.client.rpc.OntologyInfo;
import org.mmisw.ontmd.gwt.client.rpc.LoginResult;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.Hyperlink;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/**
 * ontology table.
 * 
 * @author Carlos Rueda
 */
public class OntologyTable extends FlexTable {

	private static final boolean HYPERLINK = true;
	private List<OntologyInfo> ontologyInfos;
	private LoginResult loginResult;
	private final FlexTable flexPanel = this;

	private HTML nameHeaderHtml = new HTML("Name");
	private HTML authorHeaderHtml = new HTML("Author");
	private HTML dateHeaderHtml = new HTML("Date");
	
	private String sortColumn = "name";
	
	private int sortFactor = 1;
	
	private Comparator<OntologyInfo> cmp = new Comparator<OntologyInfo>() {
		public int compare(OntologyInfo o1, OntologyInfo o2) {
			String s1 = o1.getDisplayLabel();
			String s2 = o2.getDisplayLabel();
			if ( sortColumn.equalsIgnoreCase("name") ) {
				s1 = o1.getDisplayLabel();
				s2 = o2.getDisplayLabel();
			}
			else if ( sortColumn.equalsIgnoreCase("author") ) {
				s1 = o1.getContactName();
				s2 = o2.getContactName();
			}
			else if ( sortColumn.equalsIgnoreCase("date") ) {
				s1 = o1.getDateCreated();
				s2 = o2.getDateCreated();
			}
			else {
				s1 = o1.getDisplayLabel();
				s2 = o2.getDisplayLabel();
			}
			
			return sortFactor * s1.compareToIgnoreCase(s2);
		}
	};

	private ClickListener columnHeaderClickListener = new ClickListener() {
		public void onClick(Widget sender) {
			String colName = ((HTML) sender).getText().toLowerCase();
			if ( sortColumn.equalsIgnoreCase(colName) ) {
				sortFactor *= -1;
			}
			else {
				sortColumn = colName;
			}

			showProgress();
			DeferredCommand.addCommand(new Command() {
				public void execute() {
					Collections.sort(ontologyInfos, cmp);
					update();
				}
			});
		}
	};

	
	OntologyTable() {
		super();
		
//		flexPanel.setBorderWidth(1);
		flexPanel.setWidth("100%");
		flexPanel.setStylePrimaryName("OntologyTable");
		
		nameHeaderHtml.addClickListener(columnHeaderClickListener);
		authorHeaderHtml.addClickListener(columnHeaderClickListener);
		dateHeaderHtml.addClickListener(columnHeaderClickListener);
		
		prepareHeader();
	}
	
	public void clear() {
		super.clear();
		while ( getRowCount() > 0 ) {
			removeRow(0);
		}
	}
	
	void showProgress() {
		flexPanel.clear();
		prepareHeader();
		int row = 1;
		flexPanel.getFlexCellFormatter().setColSpan(row, 0, 3);
		flexPanel.setWidget(row, 0, new HTML("<i>one moment...</i>"));
		flexPanel.getFlexCellFormatter().setAlignment(row, 0, 
				HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_MIDDLE
		);

	}

	private void prepareHeader() {
		int row = 0;
		
		flexPanel.getRowFormatter().setStylePrimaryName(row, "OntologyTable-header");
		
		flexPanel.setWidget(row, 0, nameHeaderHtml);
		flexPanel.getFlexCellFormatter().setAlignment(row, 0, 
				HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_MIDDLE
		);
		
		flexPanel.setWidget(row, 1, authorHeaderHtml);
		flexPanel.getFlexCellFormatter().setAlignment(row, 1, 
				HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_MIDDLE
		);

		flexPanel.setWidget(row, 2, dateHeaderHtml);
		flexPanel.getFlexCellFormatter().setAlignment(row, 2, 
				HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_MIDDLE
		);

		row++;
	}
	
	void setOntologyInfos(final List<OntologyInfo> ontologyInfos, LoginResult loginResult) {
		this.ontologyInfos = ontologyInfos;
		this.loginResult = loginResult;
		
		DeferredCommand.addCommand(new Command() {
			public void execute() {
				Collections.sort(ontologyInfos, cmp);
				update();
			}
		});
	}
	
	private String createLink(String uri) {
		
		String link = GWT.getModuleBaseURL()+ "?ontologyUri=" +uri;
		
		if ( loginResult != null ) {
			link += "&userId=" +loginResult.getUserId();
			link += "&sessionid=" +loginResult.getSessionId();
		}

		return link;
	}
	
	private void update() {
		
		flexPanel.clear();
		prepareHeader();
		int row = 1;
		
		for ( OntologyInfo oi : ontologyInfos ) {
			flexPanel.getRowFormatter().setStylePrimaryName(row, "OntologyTable-row");
			
			String name = oi.getDisplayLabel();
			String uri = oi.getUri();
			String author = oi.getContactName();
			String date = oi.getDateCreated();
			
			String tooltip = uri;
			
			Widget nameWidget;
			
			if ( HYPERLINK ) {
				PortalMainPanel.historyTokenMap.put(uri, oi);
				Hyperlink hlink = new Hyperlink(name, uri);
				nameWidget = hlink;
			}
			else {
				String link = createLink(uri);

				HTML html = new HTML("<a target=\"_blank\" href=\"" +link+ "\">" +name+ "</a>");
				if ( loginResult != null ) {
					// TODO show username instead of userId
					tooltip += " (uploaded by " +oi.getUserId()+ ")";
				}
				nameWidget = html;
			}
			
			nameWidget.setTitle(tooltip);
			
			
			flexPanel.setWidget(row, 0, nameWidget);
			flexPanel.getFlexCellFormatter().setAlignment(row, 0, 
					HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_MIDDLE
			);
			
			flexPanel.setWidget(row, 1, new Label(author));
			flexPanel.getFlexCellFormatter().setAlignment(row, 1, 
					HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_MIDDLE
			);
			
			flexPanel.setWidget(row, 2, new Label(date));
			flexPanel.getFlexCellFormatter().setAlignment(row, 2, 
					HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_MIDDLE
			);
			row++;
		}

	}
	

}