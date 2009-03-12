package org.mmisw.ont;

import java.net.URISyntaxException;

import junit.framework.TestCase;

/**
 * @author Carlos Rueda
 */
public class MmiUriTest extends TestCase {

	String fullRequestedUri = "http://mmisw.org/ont/mmi/someVocab.owl/someTerm";
	String requestedUri = "/ont/mmi/someVocab.owl/someTerm";
	String contextPath = "/ont";
	
	
    public void testBasic0() throws URISyntaxException {
    	MmiUri mmiUri = MmiUri.create("http://mmisw.org/ont/mmi/20081101/someVocab.owl/someTerm");
    
		assertEquals("http://mmisw.org/ont/mmi/20081101/someVocab.owl", mmiUri.getOntologyUri());
        assertEquals("mmi", mmiUri.getAuthority());
        assertEquals("20081101", mmiUri.getVersion());
        assertEquals("someVocab.owl", mmiUri.getTopic());
        assertEquals("someTerm", mmiUri.getTerm());
        assertEquals(".owl", mmiUri.getTopicExtension());
    }
    
    public void testBasic() throws URISyntaxException {
    	MmiUri mmiUri = new MmiUri(fullRequestedUri, requestedUri, contextPath);
    
		assertEquals("http://mmisw.org/ont/mmi/someVocab.owl", mmiUri.getOntologyUri());
        assertEquals("mmi", mmiUri.getAuthority());
        assertEquals("someVocab.owl", mmiUri.getTopic());
        assertEquals("someTerm", mmiUri.getTerm());
        assertEquals(".owl", mmiUri.getTopicExtension());
    }
    
    public void testTermUris() throws URISyntaxException {
    	MmiUri mmiUri = new MmiUri(fullRequestedUri, requestedUri, contextPath);
    
    	assertEquals(".owl", mmiUri.getTopicExtension());
    	
        assertEquals("http://mmisw.org/ont/mmi/someVocab#someTerm", mmiUri.getTermUri(true, "#"));
        assertEquals("http://mmisw.org/ont/mmi/someVocab/someTerm", mmiUri.getTermUri(true, "/"));
        
        assertEquals("http://mmisw.org/ont/mmi/someVocab.owl#someTerm", mmiUri.getTermUri(false, "#"));
    }

    public void testNoTerm() throws URISyntaxException {
    	String fullRequestedUri = "http://mmisw.org/ont/mmi/someVocab";
    	String requestedUri = "/ont/mmi/someVocab";
    	MmiUri mmiUri = new MmiUri(fullRequestedUri, requestedUri, contextPath);
    
    	assertEquals("", mmiUri.getTerm());
    	assertEquals("mmi", mmiUri.getAuthority());
    	assertEquals("someVocab", mmiUri.getTopic());
    	assertEquals(null, mmiUri.getVersion());
    }

    public void testNoTerm2() throws URISyntaxException {
    	String fullRequestedUri = "http://mmisw.org/ont/mmi/someVocab/";
    	String requestedUri = "/ont/mmi/someVocab/";
    	MmiUri mmiUri = new MmiUri(fullRequestedUri, requestedUri, contextPath);
    
    	assertEquals("", mmiUri.getTerm());
    	assertEquals(null, mmiUri.getVersion());
    }

    public void testTopicExt() throws URISyntaxException {
    	// topic without extension
    	String fullRequestedUri = "http://mmisw.org/ont/mmi/someVocab/someTerm";
    	String requestedUri = "/ont/mmi/someVocab/someTerm";
    	MmiUri mmiUri = new MmiUri(fullRequestedUri, requestedUri, contextPath);
    
    	assertEquals("", mmiUri.getTopicExtension());

    	// ontologyUri with an extension:
        assertEquals("http://mmisw.org/ont/mmi/someVocab.owl", mmiUri.getOntologyUriWithTopicExtension(".owl"));
    }

    public void testVersionNull() throws URISyntaxException {
    	String fullRequestedUri = "http://mmisw.org/ont/mmi/someVocab/someTerm";
    	String requestedUri = "/ont/mmi/someVocab/someTerm";
    	MmiUri mmiUri = new MmiUri(fullRequestedUri, requestedUri, contextPath);
    
    	assertEquals(null, mmiUri.getVersion());
    }
    public void testVersions() throws URISyntaxException {
    	MmiUri.checkVersion("2008");
    	MmiUri.checkVersion("200810");
    	MmiUri.checkVersion("20081030");
    	MmiUri.checkVersion("20081030T21");
    	MmiUri.checkVersion("20081030T2130");
    	MmiUri.checkVersion("20081030T213059");
    	MmiUri.checkVersion(MmiUri.LATEST_VERSION_INDICATOR);
    }
    public void testVersion1() throws URISyntaxException {
    	String fullRequestedUri = "http://mmisw.org/ont/mmi/20081021/someVocab/someTerm";
    	String requestedUri = "/ont/mmi/20081021/someVocab/someTerm";
    	MmiUri mmiUri = new MmiUri(fullRequestedUri, requestedUri, contextPath);
    
    	assertEquals("20081021", mmiUri.getVersion());
    }
    public void testVersionInvalid() throws URISyntaxException {
    	String fullRequestedUri = "http://mmisw.org/ont/mmi/2008x1021/someVocab/someTerm";
    	String requestedUri = "/ont/mmi/2008x1021/someVocab/someTerm";
    	try {
    		new MmiUri(fullRequestedUri, requestedUri, contextPath);
    		fail(); // test fails!
    	}
    	catch (URISyntaxException ok) {
    	}
    }
    public void testTopicExtAndVersion() throws URISyntaxException {
    	String fullRequestedUri = "http://mmisw.org/ont/mmi/20081021/someVocab.owl/someTerm";
    	String requestedUri = "/ont/mmi/20081021/someVocab.owl/someTerm";
    	MmiUri mmiUri = new MmiUri(fullRequestedUri, requestedUri, contextPath);
    
    	assertEquals(".owl", mmiUri.getTopicExtension());

    	// ontologyUri with an extension:
        assertEquals("http://mmisw.org/ont/mmi/20081021/someVocab.n3", mmiUri.getOntologyUriWithTopicExtension(".n3"));
    }

    public void testChangeExt1() throws URISyntaxException {
    	String fullRequestedUri = "http://mmisw.org/ont/a/20081021/v/t";
    	String requestedUri = "/ont/a/20081021/v/t";
    	MmiUri mmiUri = new MmiUri(fullRequestedUri, requestedUri, contextPath);
    
    	assertEquals("", mmiUri.getTopicExtension());
    	assertEquals("http://mmisw.org/ont/a/20081021/v.owl", mmiUri.getOntologyUriWithTopicExtension(".owl"));
    }
    public void testChangeExt2() throws URISyntaxException {
    	String fullRequestedUri = "http://mmisw.org/ont/a/20081021/v.owl/t";
    	String requestedUri = "/ont/a/20081021/v.owl/t";
    	MmiUri mmiUri = new MmiUri(fullRequestedUri, requestedUri, contextPath);
    
    	assertEquals(".owl", mmiUri.getTopicExtension());
    	assertEquals("http://mmisw.org/ont/a/20081021/v", mmiUri.getOntologyUriWithTopicExtension(""));
    }

    public void testCloneCopy() throws URISyntaxException {
    	String fullRequestedUri = "http://mmisw.org/ont/a/20081021/v.owl/t";
    	String requestedUri = "/ont/a/20081021/v.owl/t";
    	MmiUri mmiUri = new MmiUri(fullRequestedUri, requestedUri, contextPath);
    
    	MmiUri clone = mmiUri.clone();
		assertEquals(mmiUri, clone);
		
		MmiUri copyVer;
		
		copyVer = mmiUri.copyWithVersion(null);
		assertEquals("http://mmisw.org/ont/a/v.owl", copyVer.getOntologyUri());
		
		copyVer = mmiUri.copyWithVersion("20210121");
		assertEquals("http://mmisw.org/ont/a/20210121/v.owl", copyVer.getOntologyUri());
		
		copyVer = mmiUri.copyWithVersionNoCheck("%myversion%");
		assertEquals("http://mmisw.org/ont/a/%myversion%/v.owl", copyVer.getOntologyUri());
    }
    
}