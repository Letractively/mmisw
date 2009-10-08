package org.mmisw.ontmd.gwt.client.portal.extont;

import java.util.HashMap;
import java.util.Map;

import org.mmisw.iserver.gwt.client.rpc.CreateOntologyInfo;
import org.mmisw.iserver.gwt.client.rpc.HostingType;
import org.mmisw.iserver.gwt.client.rpc.OtherDataCreationInfo;
import org.mmisw.iserver.gwt.client.rpc.TempOntologyInfo;
import org.mmisw.ontmd.gwt.client.Main;
import org.mmisw.ontmd.gwt.client.portal.PortalControl;
import org.mmisw.ontmd.gwt.client.portal.PortalMainPanel;
import org.mmisw.ontmd.gwt.client.portal.md.MetadataSection1;
import org.mmisw.ontmd.gwt.client.portal.md.MetadataSection2;
import org.mmisw.ontmd.gwt.client.portal.md.MetadataSection3;
import org.mmisw.ontmd.gwt.client.portal.wizard.WizardBase;
import org.mmisw.ontmd.gwt.client.portal.wizard.WizardPageBase;

import com.google.gwt.user.client.Window;

/**
 * Sequence of wizard pages to register an external ontology.
 * 
 * <p>
 * TODO complete implementation
 * 
 * @author Carlos Rueda
 */
public class RegisterExternalOntologyWizard extends WizardBase {
	
	private final RegisterExternalOntologyPage1 page1 = new RegisterExternalOntologyPage1(this);
	
	private final RegisterExternalOntologyPage2 page2 = new RegisterExternalOntologyPage2(this);

	///////////////////////////////////////////////////////////////////////////////////
	// fully-hosted type pages
	private RegisterExternalOntologyPageFullyHosted pageFullyHosted;
	private RegisterExternalOntologyMetadataPage pageFullyHostedMetadataPage1;
	private RegisterExternalOntologyMetadataPage pageFullyHostedMetadataPage2;
	private RegisterExternalOntologyMetadataPage pageFullyHostedMetadataPage3;
	private RegisterExternalOntologyPageFullyHostedConfirmation pageFullyHostedConfirmation;

	
	///////////////////////////////////////////////////////////////////////////////////
	// re-hosted type pages
	private RegisterExternalOntologyPageReHosted pageReHosted;
	private RegisterExternalOntologyMetadataPage pageReHostedMetadataPage1;
	private RegisterExternalOntologyMetadataPage pageReHostedMetadataPage2;
	private RegisterExternalOntologyMetadataPage pageReHostedMetadataPage3;
	private RegisterExternalOntologyPageReHostedConfirmation pageReHostedConfirmation;

	
	///////////////////////////////////////////////////////////////////////////////////
	// indexed type pages
	private RegisterExternalOntologyPageIndexed pageIndexed;
	
	
	
	// TODO
	//private RegisterExternalOntologyPageReHostedConfirmation pageReHostedConfirmation;
	
	// TODO
//	private private RegisterExternalOntologyPageIndexedConfirmation pageIndexedConfirmation;
	
	
	
	// provided by page1
	TempOntologyInfo tempOntologyInfo;
	
	
	private HostingType hostingType;

	/**
	 * @param portalMainPanel 
	 */
	public RegisterExternalOntologyWizard(PortalMainPanel portalMainPanel) {
		super(portalMainPanel);
		contents.setSize("650px", "300px");
		
		contents.add(page1.getWidget());
		statusLoad.setText("");
	}
	
	
	void ontologyInfoObtained(TempOntologyInfo tempOntologyInfo) {
		assert tempOntologyInfo.getError() == null;
		
		this.tempOntologyInfo = tempOntologyInfo;
	}
	
	@Override
	protected void pageNext(WizardPageBase cp) {
		RegisterExternalOntologyPageBase currentPage = (RegisterExternalOntologyPageBase) cp;
//		if ( tempOntologyInfo == null ) { TODO apply after testing
//			return;
//		}
		
		if ( currentPage == page1 ) {
			contents.clear();
			contents.add(page2.getWidget());
		}
		else if ( currentPage == page2 ) {
			assert hostingType != null;
			
			RegisterExternalOntologyPageBase nextPage = null;
			
			switch ( hostingType ) {
			case FULLY_HOSTED:
				if ( pageFullyHosted == null ) {
					pageFullyHosted = new RegisterExternalOntologyPageFullyHosted(this);
					pageReHosted = null;
					pageIndexed = null;
				}
				nextPage = pageFullyHosted;
				break;
			case RE_HOSTED:
				if ( pageReHosted == null ) {
					pageReHosted = new RegisterExternalOntologyPageReHosted(this);
					pageFullyHosted = null;
					pageIndexed = null;
				}
				pageReHosted.updateUri(tempOntologyInfo.getXmlBase());
				nextPage = pageReHosted;
				break;
			case INDEXED:
				if ( pageIndexed == null ) {
					pageIndexed = new RegisterExternalOntologyPageIndexed(this);
					pageReHosted = null;
					pageFullyHosted = null;
				}
				pageIndexed.updateUri(tempOntologyInfo.getXmlBase());
				nextPage = pageIndexed;
				break;
			}
			
			if ( nextPage != null ) {
				contents.clear();
				contents.add(nextPage.getWidget());
				nextPage.activate();
			}
		}
		
		///////////////////////////////////////////////////////////////////////////////////
		// fully-hosted type pages

		else if ( currentPage == pageFullyHosted ) {
			if ( pageFullyHostedMetadataPage1 == null ) {
				pageFullyHostedMetadataPage1 = new RegisterExternalOntologyMetadataPage(this, 
				new MetadataSection1(HostingType.FULLY_HOSTED) {
					protected void formChanged() {
						pageFullyHostedMetadataPage1.formChanged();
					}
				});
			}
			contents.clear();
			contents.add(pageFullyHostedMetadataPage1.getWidget());
		}
		else if ( currentPage == pageFullyHostedMetadataPage1 ) {
			if ( pageFullyHostedMetadataPage2 == null ) {
				pageFullyHostedMetadataPage2 = new RegisterExternalOntologyMetadataPage(this, 
				new MetadataSection2() {
					protected void formChanged() {
						pageFullyHostedMetadataPage2.formChanged();
					}
				});
			}
			contents.clear();
			contents.add(pageFullyHostedMetadataPage2.getWidget());
		}
		else if ( currentPage == pageFullyHostedMetadataPage2 ) {
			if ( pageFullyHostedMetadataPage3 == null ) {
				pageFullyHostedMetadataPage3 = new RegisterExternalOntologyMetadataPage(this, 
				new MetadataSection3()  {
					protected void formChanged() {
						pageFullyHostedMetadataPage3.formChanged();
					}
				});
			}
			contents.clear();
			contents.add(pageFullyHostedMetadataPage3.getWidget());
		}
		else if ( currentPage == pageFullyHostedMetadataPage3 ) {
			if ( pageFullyHostedConfirmation == null ) {
				pageFullyHostedConfirmation = new RegisterExternalOntologyPageFullyHostedConfirmation(this);
			}
			contents.clear();
			contents.add(pageFullyHostedConfirmation.getWidget());
		}
		
		///////////////////////////////////////////////////////////////////////////////////
		// re-hosted type pages

		else if ( currentPage == pageReHosted ) {
			if ( pageReHostedMetadataPage1 == null ) {
				pageReHostedMetadataPage1 = new RegisterExternalOntologyMetadataPage(this, 
				new MetadataSection1(HostingType.RE_HOSTED) {
					protected void formChanged() {
						pageReHostedMetadataPage1.formChanged();
					}
				});
			}
			contents.clear();
			contents.add(pageReHostedMetadataPage1.getWidget());
		}
		else if ( currentPage == pageReHostedMetadataPage1 ) {
			if ( pageReHostedMetadataPage2 == null ) {
				pageReHostedMetadataPage2 = new RegisterExternalOntologyMetadataPage(this, 
				new MetadataSection2() {
					protected void formChanged() {
						pageReHostedMetadataPage2.formChanged();
					}
				});
			}
			contents.clear();
			contents.add(pageReHostedMetadataPage2.getWidget());
		}
		else if ( currentPage == pageReHostedMetadataPage2 ) {
			if ( pageReHostedMetadataPage3 == null ) {
				pageReHostedMetadataPage3 = new RegisterExternalOntologyMetadataPage(this, 
				new MetadataSection3()  {
					protected void formChanged() {
						pageReHostedMetadataPage3.formChanged();
					}
				});
			}
			contents.clear();
			contents.add(pageReHostedMetadataPage3.getWidget());
		}
		else if ( currentPage == pageReHostedMetadataPage3 ) {
			if ( pageReHostedConfirmation == null ) {
				pageReHostedConfirmation = new RegisterExternalOntologyPageReHostedConfirmation(this);
			}
			contents.clear();
			contents.add(pageReHostedConfirmation.getWidget());
		}
		
	}
	
	@Override
	protected void pageBack(WizardPageBase cp) {
		RegisterExternalOntologyPageBase currentPage = (RegisterExternalOntologyPageBase) cp;
		if ( currentPage == page2 ) {
			contents.clear();
			contents.add(page1.getWidget());
		}
		else if ( currentPage == pageFullyHosted 
		||   currentPage == pageReHosted 
		||   currentPage == pageIndexed 
		) {
			contents.clear();
			contents.add(page2.getWidget());
		}
		
		else if ( currentPage == pageFullyHostedMetadataPage1
		     ||   currentPage == pageReHostedMetadataPage1
		) {
			RegisterExternalOntologyPageBase nextPage = null;
			if ( pageFullyHosted != null ) {
				nextPage = pageFullyHosted;
			}
			else if ( pageReHosted != null ) {
				nextPage = pageReHosted;
			}
			else if ( pageIndexed != null ) {
				nextPage = pageIndexed;
			}
			
			if ( nextPage != null ) {
				contents.clear();
				contents.add(nextPage.getWidget());
			}
		}
		else if ( currentPage == pageFullyHostedMetadataPage2 ) {
			contents.clear();
			contents.add(pageFullyHostedMetadataPage1.getWidget());
		}
		else if ( currentPage == pageFullyHostedMetadataPage3 ) {
			contents.clear();
			contents.add(pageFullyHostedMetadataPage2.getWidget());
		}
		else if ( currentPage == pageFullyHostedConfirmation ) {
			contents.clear();
			contents.add(pageFullyHostedMetadataPage3.getWidget());
		}
		
		else if ( currentPage == pageReHostedMetadataPage2 ) {
			contents.clear();
			contents.add(pageReHostedMetadataPage1.getWidget());
		}
		else if ( currentPage == pageReHostedMetadataPage3 ) {
			contents.clear();
			contents.add(pageReHostedMetadataPage2.getWidget());
		}
		else if ( currentPage == pageReHostedConfirmation ) {
			contents.clear();
			contents.add(pageReHostedMetadataPage3.getWidget());
		}
		
	}


	void hostingTypeSelected(HostingType hostingType) {
		this.hostingType = hostingType;
		Main.log("hostingTypeSelected: " +hostingType);
	}


	@Override
	protected void finish(WizardPageBase cp) {
		RegisterExternalOntologyPageBase currentPage = (RegisterExternalOntologyPageBase) cp;
		
		if ( tempOntologyInfo == null ) {
			// this should not normally happen -- only while I'm testing other functionalities
			Window.alert("No ontology info has been specified--Please report this bug.");
			return;
		}
		if ( PortalControl.getInstance().getLoginResult() == null
		||   PortalControl.getInstance().getLoginResult().getError() != null
		) {
			// this should not normally happen -- only while I'm testing other functionalities
			Window.alert("No user logged in at this point--Please report this bug.");
			return;
		}
		

		assert currentPage == pageFullyHostedConfirmation
		    || currentPage == pageReHostedConfirmation
//		    || currentPage == pageIndexedConfirmation    TODO
		;
		
		/////////////////////////////////////////////////////////////////////
		// Finish: fully hosted registration
		if ( currentPage == pageFullyHostedConfirmation ) {
			
			// collect information and run the "review and register"
			String error;
			Map<String, String> newValues = new HashMap<String, String>();
			if ( (error = pageFullyHosted.authorityShortNamePanel.putValues(newValues, true)) != null
			||   (error = pageFullyHostedMetadataPage1.mdSection.putValues(newValues, true)) != null
			||   (error = pageFullyHostedMetadataPage2.mdSection.putValues(newValues, true)) != null
			||   (error = pageFullyHostedMetadataPage3.mdSection.putValues(newValues, true)) != null
			) {
				// Should not happen
				Window.alert(error);
				return;
			}
			
			CreateOntologyInfo createOntologyInfo = new CreateOntologyInfo();
			createOntologyInfo.setHostingType(HostingType.FULLY_HOSTED);
			
			createOntologyInfo.setMetadataValues(newValues);
			
			OtherDataCreationInfo dataCreationInfo = new OtherDataCreationInfo();
			dataCreationInfo.setTempOntologyInfo(tempOntologyInfo);
			createOntologyInfo.setDataCreationInfo(dataCreationInfo);
			
			// set info of original ontology:
			createOntologyInfo.setBaseOntologyInfo(tempOntologyInfo);
			
			// set the desired authority/shortName combination:
			createOntologyInfo.setAuthority(pageFullyHosted.getAuthority());
			createOntologyInfo.setShortName(pageFullyHosted.getShortName());
			
			RegisterExternalOntologyExecute execute = new RegisterExternalOntologyExecute(createOntologyInfo);
			
			execute.reviewAndRegisterNewOntology();
		}
		
		/////////////////////////////////////////////////////////////////////
		// Finish: re-hosted registration
		else if ( currentPage == pageReHostedConfirmation ) {
			
			// collect information and run the "review and register"
			String error;
			Map<String, String> newValues = new HashMap<String, String>();
			if ( (error = pageReHostedMetadataPage1.mdSection.putValues(newValues, true)) != null
			||   (error = pageReHostedMetadataPage2.mdSection.putValues(newValues, true)) != null
			||   (error = pageReHostedMetadataPage3.mdSection.putValues(newValues, true)) != null
			) {
				// Should not happen
				Window.alert(error);
				return;
			}
			
			CreateOntologyInfo createOntologyInfo = new CreateOntologyInfo();
			createOntologyInfo.setHostingType(HostingType.RE_HOSTED);
			
			createOntologyInfo.setMetadataValues(newValues);
			
			OtherDataCreationInfo dataCreationInfo = new OtherDataCreationInfo();
			dataCreationInfo.setTempOntologyInfo(tempOntologyInfo);
			createOntologyInfo.setDataCreationInfo(dataCreationInfo);
			
			// set info of original ontology:
			createOntologyInfo.setBaseOntologyInfo(tempOntologyInfo);
			
			
			RegisterExternalOntologyExecute execute = new RegisterExternalOntologyExecute(createOntologyInfo);
			
			execute.reviewAndRegisterNewOntology();
		}
		
		/////////////////////////////////////////////////////////////////////
		// TODO Finish: indexed registration
//		else if ( currentPage == pageIndexedConfirmation ) {
//			
//		}
		

	}


	String getOntologyUri() {
		// TODO review for the other types of hosting
		if ( pageFullyHosted != null ) {
			return pageFullyHosted.getOntologyUri();
		}
		else {
			String xmlBase = tempOntologyInfo.getXmlBase();
			return xmlBase;
		}
	}

}
