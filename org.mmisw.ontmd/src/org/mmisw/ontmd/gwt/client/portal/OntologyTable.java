package org.mmisw.ontmd.gwt.client.portal;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.mmisw.iserver.gwt.client.rpc.LoginResult;
import org.mmisw.iserver.gwt.client.rpc.RegisteredOntologyInfo;
import org.mmisw.ontmd.gwt.client.util.Util;

import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Hyperlink;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/**
 * ontology table.
 * 
 * @author Carlos Rueda
 */
public class OntologyTable extends FlexTable {
	private static final String TESTING_ONT_MARK = "<font color=\"red\">T</font>";

// TODO Use utility ViewTable 
	
	
	static interface IQuickInfo {
		Widget getWidget(RegisteredOntologyInfo oi);
	}
	
	private IQuickInfo quickInfo;

	private List<RegisteredOntologyInfo> ontologyInfos;
	
	private boolean isAdmin = false;
	
	private final FlexTable flexPanel = this;

	private HTML quickInfoHeaderHtml = new HTML("");
	private HTML nameHeaderHtml = new HTML("Name");
	private HTML authorHeaderHtml = new HTML("Author");
	private HTML versionHeaderHtml = new HTML("Version");
	private HTML submitterHeaderHtml = new HTML("Submitter");
	
	private String sortColumn = "name";
	
	private int sortFactor = 1;
	
	private Comparator<RegisteredOntologyInfo> cmp = new Comparator<RegisteredOntologyInfo>() {
		public int compare(RegisteredOntologyInfo o1, RegisteredOntologyInfo o2) {
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
			else if ( sortColumn.equalsIgnoreCase("version") ) {
				s1 = o1.getVersionNumber();
				s2 = o2.getVersionNumber();
			}
			else if ( sortColumn.equalsIgnoreCase("submitter") ) {
				s1 = o1.getUsername();
				s2 = o2.getUsername();
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

	
	/** given by the user */
	private ClickListener clickListenerToHyperlinks;
	
	
	OntologyTable() {
		this(null);
	}
	
	OntologyTable(IQuickInfo quickInfo) {
		super();
		this.quickInfo = quickInfo;
		
		flexPanel.setBorderWidth(1);
		flexPanel.setCellPadding(1);
		flexPanel.setWidth("100%");
		flexPanel.setStylePrimaryName("OntologyTable");
		
		nameHeaderHtml.addClickListener(columnHeaderClickListener);
		authorHeaderHtml.addClickListener(columnHeaderClickListener);
		versionHeaderHtml.addClickListener(columnHeaderClickListener);
		submitterHeaderHtml.addClickListener(columnHeaderClickListener);
		
		prepareHeader();
	}
	
	/**
	 * For subsequent creation of the entries in the table, the given listener will be
	 * associated to the corresponding hyperlinks. So, call this before {@link #setOntologyInfos(List, LoginResult)}.
	 * @param clickListenerToHyperlinks
	 */
	public void addClickListenerToHyperlinks(ClickListener clickListenerToHyperlinks) {
		this.clickListenerToHyperlinks = clickListenerToHyperlinks;
	}

	public void setQuickInfo(IQuickInfo quickInfo) {
		this.quickInfo = quickInfo;
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
		
		int col = 0;
		if ( quickInfo != null ) {
			flexPanel.setWidget(row, col, quickInfoHeaderHtml);
			flexPanel.getFlexCellFormatter().setAlignment(row, col, 
					HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_MIDDLE
			);
			col++;
		}
		
		flexPanel.setWidget(row, col, nameHeaderHtml);
		flexPanel.getFlexCellFormatter().setAlignment(row, col, 
				HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_MIDDLE
		);
		col++;
		
		flexPanel.setWidget(row, col, authorHeaderHtml);
		flexPanel.getFlexCellFormatter().setAlignment(row, col, 
				HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_MIDDLE
		);
		col++;

		flexPanel.setWidget(row, col, versionHeaderHtml);
		flexPanel.getFlexCellFormatter().setAlignment(row, col, 
				HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_MIDDLE
		);
		col++;

		if ( isAdmin ) {
			flexPanel.setWidget(row, col, submitterHeaderHtml);
			flexPanel.getFlexCellFormatter().setAlignment(row, col, 
					HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_MIDDLE
			);
		}

		row++;
	}
	
	void setOntologyInfos(final List<RegisteredOntologyInfo> ontologyInfos, LoginResult loginResult) {
		this.ontologyInfos = ontologyInfos;
		this.isAdmin = loginResult != null && loginResult.isAdministrator();
		
		DeferredCommand.addCommand(new Command() {
			public void execute() {
				Collections.sort(ontologyInfos, cmp);
				update();
			}
		});
	}
	
	private void update() {
		
		flexPanel.clear();
		prepareHeader();
		int row = 1;
		
		for ( RegisteredOntologyInfo oi : ontologyInfos ) {
			flexPanel.getRowFormatter().setStylePrimaryName(row, "OntologyTable-row");
			
			String name = oi.getDisplayLabel();
			String uri = oi.getUri();
			String author = oi.getContactName();
			String version = oi.getVersionNumber();
			
			String tooltip = uri;
			
			Widget nameWidget;
			
			PortalMainPanel.historyTokenMap.put(uri, oi);
			Hyperlink hlink = new Hyperlink(name, uri);
			if ( Util.isTestingOntology(oi) ) {
				// add a mark
				HorizontalPanel hp = new HorizontalPanel();
				hp.add(hlink);
				hp.add(new HTML(TESTING_ONT_MARK));
				nameWidget = hp;
			}
			else {
				nameWidget = hlink;
			}

			if ( clickListenerToHyperlinks != null ) {
				hlink.addClickListener(clickListenerToHyperlinks);
			}
				

			nameWidget.setTitle(tooltip);
			
			int col = 0;
			if ( quickInfo != null ) {
				flexPanel.setWidget(row, col, quickInfo.getWidget(oi));
				flexPanel.getFlexCellFormatter().setAlignment(row, col, 
						HasHorizontalAlignment.ALIGN_CENTER, HasVerticalAlignment.ALIGN_MIDDLE
				);
				col++;
			}
			
			flexPanel.setWidget(row, col, nameWidget);
			flexPanel.getFlexCellFormatter().setAlignment(row, col, 
					HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_MIDDLE
			);
			col++;
			
			flexPanel.setWidget(row, col, new Label(author));
			flexPanel.getFlexCellFormatter().setAlignment(row, col, 
					HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_MIDDLE
			);
			col++;
				
			flexPanel.setWidget(row, col, new Label(version));
			flexPanel.getFlexCellFormatter().setAlignment(row, col, 
					HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_MIDDLE
			);
			col++;
			
			if ( isAdmin ) {
				flexPanel.setWidget(row, col, new Label(oi.getUsername()));
				flexPanel.getFlexCellFormatter().setAlignment(row, col, 
						HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_MIDDLE
				);
			}


			row++;
		}

	}

}