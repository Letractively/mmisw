package org.mmisw.ontmd.gwt.client.portal.md;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.mmisw.iserver.gwt.client.vocabulary.AttrDef;
import org.mmisw.ontmd.gwt.client.util.FieldWithChoose;
import org.mmisw.ontmd.gwt.client.util.TLabel;
import org.mmisw.ontmd.gwt.client.util.Util;

import com.google.gwt.user.client.ui.ChangeListener;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBoxBase;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

/**
 * 
 * @author Carlos Rueda
 */
public abstract class MetadataSection {
	protected static final String COMMON_INFO = 
		"Fields marked " +TLabel.requiredHtml+ " are required. " +
		"Use commas to separate values in multi-valued fields.";

	protected ChangeListener cl = new ChangeListener () {
		public void onChange(Widget sender) {
			formChanged();
		}
	};

	protected VerticalPanel widget = new VerticalPanel();
	
	protected String preamble = "";
	
	private FlexTable flexTable;
	
	protected AttrDef[] attrDefs = {};
	
	
	protected static class Elem {
		AttrDef attrDef;
		Widget widget;
		Elem(AttrDef attrDef, Widget widget) {
			assert attrDef != null;
			assert widget != null;
			this.attrDef = attrDef;
			this.widget = widget;
		}
	}
	
	protected List<Elem> elems;

	
	protected MetadataSection() {
	}
	
	protected void addElem(Elem elem) {
		if ( elems == null ) {
			elems = new ArrayList<Elem>();
		}
		elems.add(elem);
	}
	
	protected void createElements() {
		for (int i = 0; i < attrDefs.length; i++) {
			AttrDef attrDef = attrDefs[i];
			Elem elem = new Elem(attrDef, Util.createTextBoxBase(1, "200px", cl));
			addElem(elem);
		}
	}
	
	public Widget getWidget() {
		return widget;
	}

	protected void createForm() {
		flexTable = new FlexTable();
		int row = 0;
		
		for ( Elem elem : elems ) {
			AttrDef attrDef = elem.attrDef;
			Widget widget = elem.widget;
			
			String label = attrDef.getLabel();
			String tooltip = "<b>" +label+ "</b>:<br/>" + 
					attrDef.getTooltip() +
					"<br/><br/><div align=\"right\">(" +attrDef.getUri()+ ")</div>";
			flexTable.setWidget(row, 0, new TLabel(label, attrDef.isRequired(), tooltip ));
			
			flexTable.setWidget(row, 1, widget);
			flexTable.getFlexCellFormatter().setWidth(row, 0, "250px");
			flexTable.getFlexCellFormatter().setAlignment(row, 0, 
					HasHorizontalAlignment.ALIGN_RIGHT, HasVerticalAlignment.ALIGN_MIDDLE
			);
			flexTable.getFlexCellFormatter().setAlignment(row, 1, 
					HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_MIDDLE
			);
			row++;
		}
		
		widget.setSpacing(5);
		widget.clear();
		widget.add(new HTML(preamble));
		widget.add(flexTable);
	}
	
	protected void formChanged() {
	}
	
	protected void initFields() {
		// TODO ...
		formChanged();
	}

	
	
	/**
	 * Puts the non-empty values in the given map, if not null.
	 * 
	 * @param values null to just do the checking of required attributes.
	 * @param checkMissing true to do the checking. So, if just want to get the current values without
	 *         complains about missing values, pass a non-null values map and set checkMissing == false.
	 *         
	 * @return Only non-null when called with checkMissing == true and there is some missing required attribute.
	 */
	public String putValues(Map<String, String> values, boolean checkMissing) {
		
		for ( Elem elem : elems ) {
			String value = "";
			
			if ( elem.widget instanceof TextBoxBase ) {
				value = ((TextBoxBase) elem.widget).getText();
			}
			else if ( elem.widget instanceof ListBox ) {
				ListBox lb = (ListBox) elem.widget;
				value = lb.getValue(lb.getSelectedIndex());
			}
			else if ( elem.widget instanceof FieldWithChoose ) {
				value = ((FieldWithChoose) elem.widget).getTextBox().getText();
			}
			
			value = value.trim();
			
			if ( value.trim().length() == 0 
			||   value.startsWith("--")
			) {
				if ( checkMissing && elem.attrDef.isRequired() ) {
					String error = "Please provide a value for the field with label: " +elem.attrDef.getLabel();
					return error;
				}
			}
			else {
				String uri = elem.attrDef.getUri();
				value = value.trim();
				if ( values != null ) {
					// do actual assignment
					_putValueIfNonEmpty(values, uri, value);
				}
			}
		}
		return null;
	}
	/**
	 * Puts the value in the map, only if value is not null and not empty.
	 */
	private static void _putValueIfNonEmpty(Map<String, String> values, String key, String value) {
		if ( value != null && value.trim().length() > 0 ) {
			values.put(key, value);
		}
	}



	
}