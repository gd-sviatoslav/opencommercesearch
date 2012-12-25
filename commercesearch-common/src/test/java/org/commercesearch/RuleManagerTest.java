package org.commercesearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.matchers.JUnitMatchers.hasItem;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.SolrParams;
import org.commercesearch.repository.BlockRuleProperty;
import org.commercesearch.repository.BoostRuleProperty;
import org.commercesearch.repository.CategoryProperty;
import org.commercesearch.repository.FacetProperty;
import org.commercesearch.repository.FacetRuleProperty;
import org.commercesearch.repository.RuleProperty;
import org.commercesearch.repository.SearchRepositoryItemDescriptor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryItemDescriptor;

@RunWith(MockitoJUnitRunner.class)
public class RuleManagerTest {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }
    
    private static final String facetRule = "facetRule", blockRule = "blockRule", boostRule = "boostRule";
    
    @Mock private Repository repository;    
    @Mock private SolrServer server;
    @Mock private QueryResponse queryResponse;
    
    @Mock private RepositoryItem shoeItem;    
    @Mock private RepositoryItem fruitItem;    
    @Mock private RepositoryItem bottleItem;
    @Mock private RepositoryItem testRuleItem;
    
    @Mock private RepositoryItem siteA, siteB, siteC;
    @Mock private RepositoryItem cataA, cataB, cataC;
    @Mock private RepositoryItem cateA, cateB, cateC, cateCchild1, cateCchild2, cateCchild3;
    
    @Mock private RepositoryItem cateAToken1, cateAToken2;
    @Mock private RepositoryItem cateBToken;
    @Mock private RepositoryItem cateCchild1Token, cateCchild2Token;
    
    @Mock private RepositoryItemDescriptor cateDescriptor, faultyDescriptor;
    
    private static final String EXPECTED_WILDCARD = "__all__";
    
    @Before
    public void setUpSitesForCreateRuleDocument() {
        when(siteA.getRepositoryId()).thenReturn("site:alpha");
        when(siteB.getRepositoryId()).thenReturn("site:beta");
        when(siteC.getRepositoryId()).thenReturn("site:charlie");
    }
    
    @Before
    public void setUpCatalogsForCreateRuleDocument() {
        when(cataA.getRepositoryId()).thenReturn("cata:alpha");
        when(cataB.getRepositoryId()).thenReturn("cata:beta");
        when(cataC.getRepositoryId()).thenReturn("cata:charlie");
    }
    
    @Before
    public void setUpCategoriesForCreateRuleDocument() throws RepositoryException {
        // cateA has 2 search tokens
        when(cateA.getRepositoryId()).thenReturn("cate:alpha");
        when(cateA.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn(new HashSet<String>(Arrays.asList(new String[]{ "cateA:token1", "cateA:token2", })));
        // cateB has 1 search token
        when(cateB.getRepositoryId()).thenReturn("cate:beta");
        when(cateB.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn(new HashSet<String>(Arrays.asList(new String[]{ "cateB:token", })));
        // cateC has 0 search tokens
        when(cateC.getRepositoryId()).thenReturn("cate:charlie");
        // cateC has 2 children categories however
        when(cateC.getPropertyValue(CategoryProperty.CHILD_CATEGORIES)).thenReturn(new LinkedList<RepositoryItem>(Arrays.asList(new RepositoryItem[]{ cateCchild1, cateCchild2, cateCchild3, })));
        
        // cateCchildx search tokens...
        when(cateCchild1.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn(new HashSet<String>(Arrays.asList(new String[]{ "cateCchild1:token", })));
        when(cateCchild2.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn(new HashSet<String>(Arrays.asList(new String[]{ "cateCchild2:token", })));
        when(cateCchild3.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn(new HashSet<String>(Arrays.asList(new String[]{ "cateCchild3:token:INVISIBLE!!!!", })));
        
        
        // set up a descriptor for all of the category search tokens
        when(cateDescriptor.getItemDescriptorName()).thenReturn("category");
        for (RepositoryItem r : new RepositoryItem[] {cateA, cateB, cateC, cateCchild1, cateCchild2, }) {
            when(r.getItemDescriptor()).thenReturn(cateDescriptor);
        }      
        // make the INVISIBLE version... it won't get into the output
        when(faultyDescriptor.getItemDescriptorName()).thenReturn("notcategory");
        when(cateCchild3.getItemDescriptor()).thenReturn(faultyDescriptor);
    }

    private void setUpDocAndItem(String description, String id, String ruleType, RepositoryItem item, SolrDocumentList documents) throws RepositoryException {
        SolrDocument r1 = new SolrDocument();
        r1.addField("description", description);
        r1.addField("id", id);
        documents.add(r1);        
        when(item.getPropertyValue(RuleProperty.RULE_TYPE)).thenReturn(ruleType);
        when(repository.getItem(id, SearchRepositoryItemDescriptor.RULE)).thenReturn(item);           
    }  
    
    @Test 
    public void testSetRuleParamsAndSetFilterQueries() throws RepositoryException, SolrServerException {    
        // make sure that the facetManager gets addFacet called when we supply facets
        final FacetManager facetManager = mock(FacetManager.class);        
        RuleManager mgr = new RuleManager(repository, server) {
            @Override
            public FacetManager getFacetManager() {
                return facetManager;
            }
        };
                
        // we need to make sure that we test filterQueries here...
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        // ---------- set up docs with a rule type -----------
        setUpDocAndItem("i wear shoes", "supra", facetRule, shoeItem, solrDocumentList);
        // note that we do NOT add this into the Repository so that we have a null rule in loadRules, this causes this document to not go into the rules
        SolrDocument r1 = new SolrDocument();
        r1.addField("description", "avacado's grow on trees!");
        r1.addField("id", "avacado");
        solrDocumentList.add(r1);    
        setUpDocAndItem("water bottle's are nice for drinking water!", "nalgene", boostRule, bottleItem, solrDocumentList);
                       
        // ----------- set up doclist attributes ----------
        solrDocumentList.setNumFound(solrDocumentList.size()); 
        solrDocumentList.setStart(0L);
//        solrDocumentList.setMaxScore(1000.0);
        when(queryResponse.getResults()).thenReturn(solrDocumentList);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        
        // ------------ make the call to load the rules etc -------------
        RepositoryItem catalog = mock(RepositoryItem.class);
        when(catalog.getRepositoryId()).thenReturn("bobcatalog");
        SolrQuery query = mock(SolrQuery.class);
        when(query.getQuery()).thenReturn("jackets");
        
        FilterQuery[] filterQueries = new FilterQuery[] {
            new FilterQuery("category", "jackets"), // is a multi
            new FilterQuery("category", "12.jackets"), // is a multi
            new FilterQuery("hasPinStripes", "redstripes"), 
            new FilterQuery("hasFeathers", "socks&stuff"), 
            new FilterQuery("hasLaces", "raingear"), // is a multi
            new FilterQuery("chopsticks", "lookout below")
        };
        
        // set up the facet items to catch all conditions
        RepositoryItem categoryFacetItem = mock(RepositoryItem.class);
        when(facetManager.getFacetItem("category")).thenReturn(categoryFacetItem);
        when(categoryFacetItem.getPropertyValue((FacetProperty.IS_MULTI_SELECT))).thenReturn(true);
        
        RepositoryItem hasPinStripesFacetItem = mock(RepositoryItem.class);
        when(facetManager.getFacetItem("hasPinStripes")).thenReturn(hasPinStripesFacetItem);
        when(hasPinStripesFacetItem.getPropertyValue((FacetProperty.IS_MULTI_SELECT))).thenReturn(false);
        
        RepositoryItem hasFeathersFacetItem = mock(RepositoryItem.class);
        when(facetManager.getFacetItem("hasFeathers")).thenReturn(hasFeathersFacetItem);
        // don't support multi for hasFeathers...
        
        RepositoryItem hasLacesFacetItem = mock(RepositoryItem.class);
        when(facetManager.getFacetItem("hasLaces")).thenReturn(hasLacesFacetItem);
        when(hasLacesFacetItem.getPropertyValue((FacetProperty.IS_MULTI_SELECT))).thenReturn(true);        
        
        // and nothing for chopsticks

        mgr.setRuleParams(filterQueries, catalog, query);
        
        verify(query).setFacetPrefix("category", "1.bobcatalog");
        verify(query).addFilterQuery("category:0.bobcatalog");
        verify(query).getQuery();
        verify(query).addSortField("isToos", ORDER.asc);
        verify(query).addSortField("score", ORDER.desc);        
        verify(query).setFacetPrefix("category", "13.jackets"); 

        // verify the single calls to addFilterQuery
        verify(query).addFilterQuery("hasPinStripes:redstripes"); // this will have a facet
        verify(query).addFilterQuery("hasFeathers:socks&stuff"); // this will have a facet, but not MULTI
        verify(query).addFilterQuery("chopsticks:lookout below"); // no facet for this one (test null path)
        
        // now verify the multi calls to addFilterQuery
        verify(query).addFilterQuery("{!tag=category}category:jackets OR category:12.jackets");
        verify(query).addFilterQuery("{!tag=hasLaces}hasLaces:raingear");        
        
        verifyNoMoreInteractions(query);        
    }
    
    @Test 
    public void testSetRuleParams2NullRules() throws RepositoryException, SolrServerException { 
        // test handling null filterQueries
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        // ---------- set up docs with a rule type -----------
        setUpDocAndItem("i wear shoes", "supra", facetRule, shoeItem, solrDocumentList);
        // note that we do NOT add this into the Repository so that we have a null rule in loadRules, this causes this document to not go into the rules
        SolrDocument r1 = new SolrDocument();
        r1.addField("description", "avacado's grow on trees!");
        r1.addField("id", "avacado");
        solrDocumentList.add(r1);    
        setUpDocAndItem("water bottle's are nice for drinking water!", "nalgene", boostRule, bottleItem, solrDocumentList);
                       
        // ----------- set up doclist attributes ----------
        solrDocumentList.setNumFound(solrDocumentList.size()); 
        solrDocumentList.setStart(0L);
//        solrDocumentList.setMaxScore(1000.0);
        when(queryResponse.getResults()).thenReturn(solrDocumentList);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        
        // ----------- set up rule manager -------------
        
        RuleManager mgr = new RuleManager(repository, server);
        assertEquals(null, mgr.getRules());
        
        // ------------ make the call to load the rules etc -------------
        RepositoryItem catalog = mock(RepositoryItem.class);
        when(catalog.getRepositoryId()).thenReturn("bobcatalog");
        SolrQuery query = mock(SolrQuery.class);
        when(query.getQuery()).thenReturn("jackets");
        
        mgr.setRuleParams(null, catalog, query);
        verify(query).setFacetPrefix("category", "1.bobcatalog");
        verify(query).addFilterQuery("category:0.bobcatalog");
        verify(query).getQuery();
        verify(query).addSortField("isToos", ORDER.asc);
        verify(query).addSortField("score", ORDER.desc);
        verifyNoMoreInteractions(query);
    }
    
    @Test
    public void testSetRuleParamsBlocks() {
        RuleManager mgr = new RuleManager(repository, server);
        SolrQuery query = mock(SolrQuery.class);
        Map<String, List<RepositoryItem>> typeToRules = new HashMap<String, List<RepositoryItem>>();
        
        List<RepositoryItem> rules = new ArrayList<RepositoryItem>();
        typeToRules.put(blockRule, rules);
        RepositoryItem rule = mock(RepositoryItem.class);
        rules.add(rule);
        Set<RepositoryItem> blockedProducts = new HashSet<RepositoryItem>();        
        when(rule.getPropertyValue(BlockRuleProperty.BLOCKED_PRODUCTS)).thenReturn(blockedProducts);
        
        RepositoryItem blockedProduct1 = mock(RepositoryItem.class);
        blockedProducts.add(blockedProduct1);
        when(blockedProduct1.getRepositoryId()).thenReturn("walking_carpet");
        
        RepositoryItem blockedProduct2 = mock(RepositoryItem.class);
        blockedProducts.add(blockedProduct2);
        when(blockedProduct2.getRepositoryId()).thenReturn("your_highnesness");
        
        mgr.setRuleParams(query, typeToRules);
        verify(query).addSortField("isToos", ORDER.asc);
        verify(query).addFilterQuery("-productId:walking_carpet");
        verify(query).addFilterQuery("-productId:your_highnesness");
        verify(query).addSortField("score", ORDER.desc);        
    }  
    
    @Test
    public void testSetRuleParamsBoosts() {
        RuleManager mgr = new RuleManager(repository, server);
        SolrQuery query = mock(SolrQuery.class);
        Map<String, List<RepositoryItem>> typeToRules = new HashMap<String, List<RepositoryItem>>();
        
        List<RepositoryItem> rules = new ArrayList<RepositoryItem>();
        typeToRules.put(boostRule, rules);
        RepositoryItem rule = mock(RepositoryItem.class);
        rules.add(rule);
        List<RepositoryItem> boostedProducts = new ArrayList<RepositoryItem>();        
        when(rule.getPropertyValue(BoostRuleProperty.BOOSTED_PRODUCTS)).thenReturn(boostedProducts);
        
        RepositoryItem boostedProduct1 = mock(RepositoryItem.class);
        boostedProducts.add(boostedProduct1);
        when(boostedProduct1.getRepositoryId()).thenReturn("hello world");
        
        RepositoryItem boostedProduct2 = mock(RepositoryItem.class);
        boostedProducts.add(boostedProduct2);
        when(boostedProduct2.getRepositoryId()).thenReturn("i like food");
        
        mgr.setRuleParams(query, typeToRules);
        verify(query).addSortField("isToos", ORDER.asc);
        verify(query).addSortField("fixedBoost(productId,'hello world','i like food')", ORDER.desc);
        verify(query).addSortField("score", ORDER.desc);        
    }  
    
    
    @Test
    public void testSetRuleParamsFacets() {
        // make sure that the facetManager gets addFacet called when we supply facets
        final FacetManager facetManager = mock(FacetManager.class);        
        RuleManager mgr = new RuleManager(repository, server) {
            @Override
            public FacetManager getFacetManager() {
                return facetManager;
            }
        };
        SolrQuery query = mock(SolrQuery.class);
        Map<String, List<RepositoryItem>> typeToRules = new HashMap<String, List<RepositoryItem>>();
        List<RepositoryItem> rules = new ArrayList<RepositoryItem>();
        typeToRules.put(facetRule, rules);
        RepositoryItem rule = mock(RepositoryItem.class);
        rules.add(rule);
        Set<RepositoryItem> facets = new HashSet<RepositoryItem>();        
        when(rule.getPropertyValue(FacetRuleProperty.FACETS)).thenReturn(facets);
                
        RepositoryItem facet1 = mock(RepositoryItem.class);
        facets.add(facet1);
        
        RepositoryItem facet2 = mock(RepositoryItem.class);
        facets.add(facet2);
        
        mgr.setRuleParams(query, typeToRules);
        verify(facetManager).addFacet(query, facet1);
        verify(facetManager).addFacet(query, facet2);
        verifyNoMoreInteractions(facetManager);     
    }
    
    // TODO: make a good test for testSetRuleParamsFacets
    
    @Test
    public void testSetRuleParamsTypesHaveNoContent() {
        RuleManager mgr = new RuleManager(repository, server);
        SolrQuery query = mock(SolrQuery.class);
        Map<String, List<RepositoryItem>> typeToRules = new HashMap<String, List<RepositoryItem>>();
        
        List<RepositoryItem> facetRules = new ArrayList<RepositoryItem>();
        typeToRules.put(facetRule, facetRules);
        RepositoryItem frule1 = mock(RepositoryItem.class);
        facetRules.add(frule1);
        
        List<RepositoryItem> boostRules = new ArrayList<RepositoryItem>();
        typeToRules.put(boostRule, boostRules);
        RepositoryItem borule1 = mock(RepositoryItem.class);
        boostRules.add(borule1);
        
        
        List<RepositoryItem> blockRules = new ArrayList<RepositoryItem>();
        typeToRules.put(blockRule, blockRules);
        RepositoryItem blrule1 = mock(RepositoryItem.class);
        blockRules.add(blrule1);
                
        // this should NOT throw if no FACETS, BOOSTED_PRODUCTS or BLOCKED_PRODUCTS are found
        try {
            mgr.setRuleParams(query, typeToRules);
        } catch (NullPointerException ex) {
            fail("Should protect against no rules set");
        }
    }  
    
    @Test
    public void testSetRuleParamsEmptyRules() {
        RuleManager mgr = new RuleManager(repository, server);
        SolrQuery query = mock(SolrQuery.class);
        mgr.setRuleParams(query, new HashMap());
        verify(query).addSortField("isToos", ORDER.asc);
        verify(query).addSortField("score", ORDER.desc);
        verifyNoMoreInteractions(query);
    }    

    @Test
    public void testSetRuleParamsNullRules() {
        RuleManager mgr = new RuleManager(repository, server);
        SolrQuery query = mock(SolrQuery.class);
        mgr.setRuleParams(query, null);
        verifyNoMoreInteractions(query);
    }
    
    @Test
    public void testCreateRuleDocumentNullQueryAll() throws RepositoryException {
        // if you set NOTHING in the rule
        RuleManager mgr = new RuleManager(repository, server);
        when(testRuleItem.getRepositoryId()).thenReturn("superduper");
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);
        assertEquals("superduper", doc.getField("id").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("query").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("siteId").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("catalogId").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("category").getValue());        
    }
    
    @Test
    public void testCreateRuleDocumentStarQueryAll() throws RepositoryException {
        // if you use a * for the rule query & everything else is unset
        RuleManager mgr = new RuleManager(repository, server);
        when(testRuleItem.getRepositoryId()).thenReturn("superduper");
        when(testRuleItem.getPropertyValue(RuleProperty.QUERY)).thenReturn("*");
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);
        assertEquals("superduper", doc.getField("id").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("query").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("siteId").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("catalogId").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("category").getValue());        
    }
    
    @Test
    public void testCreateRuleDocumentActualQueryAll() throws RepositoryException {
        // if you use a string for the rule query & everything else is unset
        RuleManager mgr = new RuleManager(repository, server);
        when(testRuleItem.getRepositoryId()).thenReturn("myid");
        when(testRuleItem.getPropertyValue(RuleProperty.QUERY)).thenReturn("arc'teryx");
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);
        assertEquals("myid", doc.getField("id").getValue());
        assertEquals("arc'teryx", doc.getField("query").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("siteId").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("catalogId").getValue());
        assertEquals(EXPECTED_WILDCARD, doc.getField("category").getValue());        
    }
    
    @Test
    public void testCreateRuleDocumentMulti() throws RepositoryException {
        // if you use a string for each attribute that will be checked
        RuleManager mgr = new RuleManager(repository, server);
        when(testRuleItem.getRepositoryId()).thenReturn("howdy_id");
        when(testRuleItem.getPropertyValue(RuleProperty.QUERY)).thenReturn("arc'teryx");
        
        when(testRuleItem.getPropertyValue(RuleProperty.SITES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{ siteA, siteB, siteC})));
        when(testRuleItem.getPropertyValue(RuleProperty.CATALOGS)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{ cataA, cataB, cataC})));
        when(testRuleItem.getPropertyValue(RuleProperty.CATEGORIES)).thenReturn(new HashSet<RepositoryItem>(Arrays.asList(new RepositoryItem[]{ cateA, cateB, cateC })));
        
        SolrInputDocument doc = mgr.createRuleDocument(testRuleItem);
        assertEquals("howdy_id", doc.getField("id").getValue());
        assertEquals("arc'teryx", doc.getField("query").getValue());

        @SuppressWarnings("unchecked")
        ArrayList<String> sites = (ArrayList<String>) doc.getField("siteId").getValue();
        assertThat(sites, hasItem("site:alpha"));
        assertThat(sites, hasItem("site:beta"));
        assertThat(sites, hasItem("site:charlie"));
        
        @SuppressWarnings("unchecked")
        ArrayList<String> catalogs = (ArrayList<String>) doc.getField("catalogId").getValue();
        assertThat(catalogs, hasItem("cata:alpha"));
        assertThat(catalogs, hasItem("cata:beta"));
        assertThat(catalogs, hasItem("cata:charlie"));
                        
        @SuppressWarnings("unchecked")
        ArrayList<String> categories = (ArrayList<String>) doc.getField("category").getValue();
        for (String token : new String[] {
                "cateA:token1",
                "cateA:token2",
                "cateB:token",
                "cateCchild1:token",
                "cateCchild2:token",
        }) {
            assertThat(categories, hasItem(token));
        }

    }
     
    @Test(expected=IllegalArgumentException.class)
    public void testLoadRulesEmptyQuery() throws RepositoryException, SolrServerException {
        RuleManager mgr = new RuleManager(repository, server);
        mgr.loadRules("", "Men's Clothing");
    }  
    
    @Test
    public void testLoadRulesNullRule() throws RepositoryException, SolrServerException {  
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        // ---------- set up docs with a rule type -----------
        setUpDocAndItem("i wear shoes", "supra", facetRule, shoeItem, solrDocumentList);
        // note that we do NOT add this into the Repository so that we have a null rule in loadRules, this causes this document to not go into the rules
        SolrDocument r1 = new SolrDocument();
        r1.addField("description", "avacado's grow on trees!");
        r1.addField("id", "avacado");
        solrDocumentList.add(r1);    
        setUpDocAndItem("water bottle's are nice for drinking water!", "nalgene", boostRule, bottleItem, solrDocumentList);
                       
        // ----------- set up doclist attributes ----------
        solrDocumentList.setNumFound(solrDocumentList.size());
        solrDocumentList.setStart(0L);
//        solrDocumentList.setMaxScore(1000.0);
        when(queryResponse.getResults()).thenReturn(solrDocumentList);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        
        // ----------- set up rule manager -------------
        
        RuleManager mgr = new RuleManager(repository, server);
        assertEquals(null, mgr.getRules());
        
        // ------------ make the call to load the rules etc -------------
        mgr.loadRules("jackets", "Men's Clothing");
        
        // ------------ assertions about the rules that were generated ---------
        assertNotNull(mgr.getRules());
        
        Map<String, List<RepositoryItem>> rules = mgr.getRules();
        
        assertEquals(2, rules.size());
        assertThat(rules.keySet(), hasItem(facetRule));
        assertThat(rules.keySet(), hasItem(boostRule));

        List<RepositoryItem> facetItems = rules.get(facetRule);
        List<RepositoryItem> boostItems = rules.get(boostRule);

        assertEquals(1, facetItems.size());
        assertEquals(1, boostItems.size());

        assertThat(facetItems, hasItem(shoeItem));
        assertThat(boostItems, hasItem(bottleItem));
    }        
    
    @Test
    public void testLoadRulesMixedTypes() throws RepositoryException, SolrServerException {  
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        // ---------- set up docs with a rule type -----------
        setUpDocAndItem("i wear shoes", "supra", facetRule, shoeItem, solrDocumentList);
        setUpDocAndItem("avacado's grow on trees!", "avacado", blockRule, fruitItem, solrDocumentList);    
        setUpDocAndItem("water bottle's are nice for drinking water!", "nalgene", boostRule, bottleItem, solrDocumentList);
                       
        // ----------- set up doclist attributes ----------
        solrDocumentList.setNumFound(solrDocumentList.size()); 
        solrDocumentList.setStart(0L);
//        solrDocumentList.setMaxScore(1000.0);
        when(queryResponse.getResults()).thenReturn(solrDocumentList);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        
        // ----------- set up rule manager -------------
        
        RuleManager mgr = new RuleManager(repository, server);
        assertEquals(null, mgr.getRules());
        
        // ------------ make the call to load the rules etc -------------
        mgr.loadRules("jackets", "Men's Clothing");
        
        // ------------ assertions about the rules that were generated ---------
        assertNotNull(mgr.getRules());
        
        Map<String, List<RepositoryItem>> rules = mgr.getRules();
        
        assertEquals(3, rules.size());
        assertThat(rules.keySet(), hasItem(facetRule));
        assertThat(rules.keySet(), hasItem(boostRule));
        assertThat(rules.keySet(), hasItem(blockRule));

        List<RepositoryItem> facetItems = rules.get(facetRule);
        List<RepositoryItem> boostItems = rules.get(boostRule);
        List<RepositoryItem> blockItems = rules.get(blockRule);
        assertEquals(1, facetItems.size());
        assertEquals(1, boostItems.size());
        assertEquals(1, blockItems.size());
        assertThat(facetItems, hasItem(shoeItem));
        assertThat(boostItems, hasItem(bottleItem));
        assertThat(blockItems, hasItem(fruitItem));
    }
    
    @Test
    public void testLoadRulesPaging() throws RepositoryException, SolrServerException {
        // test paging over batches of results
        SolrDocumentList docList1 = new SolrDocumentList();
        SolrDocumentList docList2 = new SolrDocumentList();
        
        RepositoryItem bikeItem = mock(RepositoryItem.class);
        RepositoryItem sledItem = mock(RepositoryItem.class);
        RepositoryItem carItem = mock(RepositoryItem.class);
        RepositoryItem heliItem = mock(RepositoryItem.class);
        RepositoryItem coatItem = mock(RepositoryItem.class);
        RepositoryItem snowItem = mock(RepositoryItem.class);
        RepositoryItem farmItem = mock(RepositoryItem.class);
        RepositoryItem steakItem = mock(RepositoryItem.class);
        RepositoryItem pillowItem = mock(RepositoryItem.class);
         
        
        // ---------- set up docs with a rule type -----------
        setUpDocAndItem("i wear shoes",     "supra",    facetRule, shoeItem,  docList1); // SKIPPING due to setStart
        setUpDocAndItem("avacado's ",       "avacado",  blockRule, fruitItem, docList1);    
        setUpDocAndItem("water bottles!",   "nalgene",  boostRule, bottleItem,docList1);
        setUpDocAndItem("biking?  fun!",    "tallboy",  boostRule, bikeItem,  docList1);
        setUpDocAndItem("sleds are lame",   "suzuki",   facetRule, sledItem,  docList1);
        setUpDocAndItem("cars are lame",    "vw",       facetRule, carItem,   docList1);
        setUpDocAndItem("fly in a heli",    "heli",     boostRule, heliItem,  docList2); // SKIPPING due to setStart
        setUpDocAndItem("snow is fun",      "snow",     boostRule, snowItem,  docList2);
        setUpDocAndItem("good for food",    "farm",     blockRule, farmItem,  docList2);
        setUpDocAndItem("cows are food",    "steak",    facetRule, steakItem, docList2);
        setUpDocAndItem("coatItem",         "patagonia",boostRule, coatItem,  docList2);
        setUpDocAndItem("sleeping",         "pillow",   boostRule, pillowItem,docList2);
                       
        // ----------- set up doclist attributes ----------
        docList1.setNumFound(docList1.size() + docList2.size()); // set numfound to be both pagefuls...
        docList2.setNumFound(docList1.size() + docList2.size()); // set numfound to be both pagefuls...
        docList1.setStart(1L); // starts at 1 ****
        docList2.setStart(1L); // starts at 1 also (this will test that we respect the start field for a docList)
//        solrDocumentList.setMaxScore(1000.0);
        QueryResponse queryResponse1 = mock(QueryResponse.class);
        QueryResponse queryResponse2 = mock(QueryResponse.class);
        
        when(queryResponse1.getResults()).thenReturn(docList1);
        when(queryResponse2.getResults()).thenReturn(docList2);
//        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse1, queryResponse2);
        
        // ----------- set up rule manager -------------        
        RuleManager mgr = new RuleManager(repository, server);
        assertEquals(null, mgr.getRules());
        
        // ------------ make the call to load the rules etc -------------
        mgr.loadRules("jackets", "Men's Clothing");
        
        // ------------ assertions about the rules that were generated ---------
        assertNotNull(mgr.getRules());
        
        Map<String, List<RepositoryItem>> rules = mgr.getRules();
        
        assertEquals(3, rules.size());
        assertThat(rules.keySet(), hasItem(facetRule));
        assertThat(rules.keySet(), hasItem(boostRule));
        assertThat(rules.keySet(), hasItem(blockRule));

        List<RepositoryItem> facetItems = rules.get(facetRule);
        List<RepositoryItem> boostItems = rules.get(boostRule);
        List<RepositoryItem> blockItems = rules.get(blockRule);
        
        assertEquals(4-1/*we are skipping 1st one*/, facetItems.size());
        assertEquals(6-1/*we are skipping 1st one*/, boostItems.size());
        assertEquals(2, blockItems.size());
        
        // test facets...
        for (RepositoryItem item : new RepositoryItem[]{sledItem, carItem, steakItem, }) {
            assertThat(facetItems, hasItem(item));    
        }
        
        // test boosts...
        for (RepositoryItem item : new RepositoryItem[]{bottleItem, bikeItem, snowItem, coatItem, pillowItem,  }) {
            assertThat(boostItems, hasItem(item));    
        }
        
        // test blocks...
        for (RepositoryItem item : new RepositoryItem[]{ fruitItem, farmItem, }) {
            assertThat(blockItems, hasItem(item));    
        }
    }
    
    @Test
    public void testLoadRulesFacets() throws RepositoryException, SolrServerException {  
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        // ---------- set up docs with a rule type -----------
        setUpDocAndItem("i wear shoes", "supra", facetRule, shoeItem, solrDocumentList);
        setUpDocAndItem("avacado's grow on trees!", "avacado", facetRule, fruitItem, solrDocumentList);    
        setUpDocAndItem("water bottle's are nice for drinking water!", "nalgene", facetRule, bottleItem, solrDocumentList);
                       
        // ----------- set up doclist attributes ----------
        solrDocumentList.setNumFound(solrDocumentList.size());
        solrDocumentList.setStart(0L);
//        solrDocumentList.setMaxScore(1000.0);
        when(queryResponse.getResults()).thenReturn(solrDocumentList);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        
        // ----------- set up rule manager -------------
        
        RuleManager mgr = new RuleManager(repository, server);
        assertEquals(null, mgr.getRules());
        
        // ------------ make the call to load the rules etc -------------
        mgr.loadRules("jackets", "Men's Clothing");
        
        // ------------ assertions about the rules that were generated ---------
        assertNotNull(mgr.getRules());        
        Map<String, List<RepositoryItem>> rules = mgr.getRules();        
        assertEquals(1, rules.size());
        assertEquals(facetRule, rules.keySet().iterator().next());        
        List<RepositoryItem> facetItems = rules.get(facetRule);        
        assertEquals(3, facetItems.size());
        assertThat(facetItems, hasItem(shoeItem));
        assertThat(facetItems, hasItem(fruitItem));
        assertThat(facetItems, hasItem(bottleItem));
    }        
    
    // finished
    @Test
    public void testLoadRulesVerifyQueryWithCategory() throws RepositoryException, SolrServerException {
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        String category = "My super duper favorite Men's category";
        String searchQuery = "fantastic jackets";
        when(queryResponse.getResults()).thenReturn(solrDocumentList);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        // ----------- set up rule manager -------------
        RuleManager mgr = new RuleManager(repository, server);
        
        // ------------ make the call to load the rules etc -------------
        mgr.loadRules(searchQuery, category);
        
        // ------------ assertions about the inner solr query that was performed -----------
        ArgumentCaptor<SolrQuery> query = ArgumentCaptor.forClass(SolrQuery.class);
        verify(server).query(query.capture());
        List<String> filters = Arrays.asList(query.getValue().getFilterQueries());
        assertEquals(1, filters.size()); 
        assertEquals("(category:__all__ OR category:" + category + ") AND siteId:__all__ AND catalogId:__all__", filters.get(0));
        assertEquals("(" + searchQuery + ")^2 OR query:__all__", query.getValue().getQuery());
    }
    
    @Test
    public void testLoadRulesVerifyQueryWithoutCategory() throws RepositoryException, SolrServerException {
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        String category = "";
        String searchQuery = "fantastic jackets";
        when(queryResponse.getResults()).thenReturn(solrDocumentList);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        // ----------- set up rule manager -------------
        RuleManager mgr = new RuleManager(repository, server);
        
        // ------------ make the call to load the rules etc -------------
        mgr.loadRules(searchQuery, category);
        
        // ------------ assertions about the inner solr query that was performed -----------
        ArgumentCaptor<SolrQuery> query = ArgumentCaptor.forClass(SolrQuery.class);
        verify(server).query(query.capture());
        List<String> filters = Arrays.asList(query.getValue().getFilterQueries());
        assertEquals(1, filters.size()); 
        assertEquals("(category:__all__) AND siteId:__all__ AND catalogId:__all__", filters.get(0));
        assertEquals("(" + searchQuery + ")^2 OR query:__all__", query.getValue().getQuery());
    }
    
    // finished
    @Test
    public void testLoadRulesNullResults() throws RepositoryException, SolrServerException {
        // test the null queryResponse path
        when(queryResponse.getResults()).thenReturn(null);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        
        // ----------- set up rule manager -------------        
        RuleManager mgr = new RuleManager(repository, server);
        assertEquals(null, mgr.getRules());
        
        // ------------ make the call to load the rules etc -------------
        mgr.loadRules("pants", "Women's Clothing");
        assertNotNull(mgr.getRules());
        assertEquals(0, mgr.getRules().size());
    }
    
    // finished
    @Test
    public void testLoadRulesEmptyResults() throws RepositoryException, SolrServerException {
        // test the empty queryResponse path
        SolrDocumentList solrDocumentList = new SolrDocumentList();        
        when(queryResponse.getResults()).thenReturn(solrDocumentList);
        when(server.query(any(SolrParams.class))).thenReturn(queryResponse);
        
        // ----------- set up rule manager -------------        
        RuleManager mgr = new RuleManager(repository, server);
        assertEquals(null, mgr.getRules());
        
        // ------------ make the call to load the rules etc -------------
        mgr.loadRules("pants", "Women's Clothing");
        assertNotNull(mgr.getRules());
        assertEquals(0, mgr.getRules().size());
    }
    

}