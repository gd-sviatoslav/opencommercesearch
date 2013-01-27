package org.opencommercesearch;

import atg.multisite.Site;
import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.Group;
import org.apache.solr.client.solrj.response.GroupCommand;
import org.apache.solr.client.solrj.response.GroupResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.junit.Before;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.opencommercesearch.junit.SearchTest;
import org.opencommercesearch.junit.runners.SearchJUnit4ClassRunner;
import org.opencommercesearch.repository.RedirectRuleProperty;
import org.opencommercesearch.repository.SearchRepositoryItemDescriptor;

import static org.junit.Assert.*;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author rmerizalde
 */
@Category(IntegrationSearchTest.class)
@RunWith(SearchJUnit4ClassRunner.class)
public class AbstractSearchServerIntegrationTest {

    @Mock
    private Site site;

    @Mock
    private RepositoryItem catalog;

    @Mock
    private RepositoryItem redirectRule;
    
    @Mock
    private Repository searchRepository;
    
    @Before
    public void setUp() throws RepositoryException {
        initMocks(this);
        when(site.getRepositoryId()).thenReturn("outdoorSite");
        when(site.getPropertyValue("defaultCatalog")).thenReturn(catalog);
        when(catalog.getRepositoryId()).thenReturn("mycatalog");
        
        when(searchRepository.getItem("redirectRuleId", SearchRepositoryItemDescriptor.RULE)).thenReturn(redirectRule);
        when(redirectRule.getPropertyValue(RedirectRuleProperty.URL)).thenReturn("/redirect");
        when(redirectRule.getPropertyValue(RedirectRuleProperty.RULE_TYPE)).thenReturn("redirectRule");
    }

    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml")
    public void testSearchCategoryName(SearchServer server) throws SearchServerException {
        testSearchCategoryAux(server, "shoe", "TNF3137");
    }

    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml")
    public void testSearchCategoryNameSynonyms(SearchServer server) throws SearchServerException {
        testSearchCategoryAux(server, "sneaker", "TNF3137");
    }
    
    @SearchTest(newInstance = true, rulesData = "/rules/redirect.xml")
    public void testSearchSedirect(SearchServer server) throws SearchServerException {
        AbstractSearchServer baseServer = (AbstractSearchServer) server;
        baseServer.setSearchRepository(searchRepository);
        
        SolrQuery query = new SolrQuery("redirect");
        SearchResponse res = server.search(query, site);
        assertEquals("/redirect", res.getRedirectResponse());
    }

    
    
    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml")
    public void testBrowseCategory(SearchServer server) throws SearchServerException {
        //scenario where we want to display only the top level categories. no results
        BrowseOptions options = new BrowseOptions(true, false, false,  100, null, "cat3000003", "mycatalog.cat3000003", "mycatalog");                
        SolrQuery query = new SolrQuery();
        SearchResponse response = server.browse(options, query, site, null);
        
        assertEquals(1, response.getQueryResponse().getGroupResponse().getValues().size());        
        validateFilterByCat3000003(response);
        validateCategoryPathNotInFacets(response);
        
        //scenario where we want to show results. not only display the top level categories
        options = new BrowseOptions(false, false, false,  100, null, "cat3000003", "mycatalog.cat3000003", "mycatalog");                
        query = new SolrQuery();
        response = server.browse(options, query, site, null);
        
        validateCategoryPathNotInFacets(response);
        assertEquals(1, response.getQueryResponse().getGroupResponse().getValues().size());        
        assertNull(response.getCategoryGraph());

    }


    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml")
    public void testBrowseBrand(SearchServer server) throws SearchServerException {
        //scenario where we want to display only the top level categories for products that have a specific brand
        BrowseOptions options = new BrowseOptions(true, false, false,  100, "88", null, null, "mycatalog");                
        SolrQuery query = new SolrQuery();
        SearchResponse response = server.browse(options, query, site, null);
        
        assertEquals(1, response.getQueryResponse().getGroupResponse().getValues().size());        
        validateFilterByTopLevelCat(response);
        validateCategoryPathNotInFacets(response);
        
        //scenario where we want to show results for products from a given brand. Not only display the top level categories
        options = new BrowseOptions(false, true, false,  100, "88", null, null, "mycatalog");                
        query = new SolrQuery();
        response = server.browse(options, query, site, null);
        
        assertEquals(1, response.getQueryResponse().getGroupResponse().getValues().size());        
        validateFilterByTopLevelCat(response);
        validateCategoryPathNotInFacets(response);
        
        //scenario where we want to show results for products from a given brand and category. Not only display the top level categories
        options = new BrowseOptions(false, true, false,  100, "88", "cat3000003", "mycatalog.cat3000003", "mycatalog");                
        query = new SolrQuery();
        response = server.browse(options, query, site, null);
        
        assertEquals(1, response.getQueryResponse().getGroupResponse().getValues().size());        
        validateFilterByCat3000003(response);
        validateCategoryPathNotInFacets(response);

    }

    @SearchTest(newInstance = true, productData = "/product_catalog/sandal.xml")
    public void testOnSale(SearchServer server) throws SearchServerException {
        
        //scenario where we want to display only the top level categories for products that are on sale.
        BrowseOptions options = new BrowseOptions(true, false, true,  100, null, null, null, "mycatalog");                
        SolrQuery query = new SolrQuery();
        SearchResponse response = server.browse(options, query, site, null);
        
        assertEquals(1, response.getQueryResponse().getGroupResponse().getValues().size());
        validateFilterByTopLevelCat(response);
        validateCategoryPathNotInFacets(response);
        
        //scenario where we want to show results for products that are on sale. not only display the top level categories
        options = new BrowseOptions(false, false, true,  100, null, null, null, "mycatalog");                
        query = new SolrQuery();
        response = server.browse(options, query, site, null);
        
        validateCategoryPathNotInFacets(response);
        assertEquals(1, response.getQueryResponse().getGroupResponse().getValues().size());        
        assertNull(response.getCategoryGraph());
        
    }

    @SearchTest(newInstance = true)
    public void testDeleteByQuery(SearchServer server) throws SearchServerException {
        SolrQuery query = new SolrQuery("jacket");
        SearchResponse res = server.search(query, site);
        QueryResponse queryResponse = res.getQueryResponse();
        GroupResponse groupResponse = queryResponse.getGroupResponse();

        assertNotEquals(new Integer(1), groupResponse.getValues().get(0).getNGroups());

        server.deleteByQuery("*:*");
        server.commit();
        res = server.search(query, site);
        queryResponse = res.getQueryResponse();
        groupResponse = queryResponse.getGroupResponse();
        assertEquals(new Integer(0), groupResponse.getValues().get(0).getNGroups());
    }
    
    
    
    protected void validateFilterByTopLevelCat(SearchResponse response) {
        assertEquals(1, response.getQueryResponse().getGroupResponse().getValues().size());        
        assertEquals("cat3000003",   response.getCategoryGraph().get(0).getId());
        assertEquals("cat3100004",   response.getCategoryGraph().get(0).getCategoryGraphNodes().get(0).getId());
        assertEquals("cat31000077",  response.getCategoryGraph().get(0).getCategoryGraphNodes().get(1).getId());
        assertEquals("cat4000003",   response.getCategoryGraph().get(1).getId());
        assertEquals("cat410000178", response.getCategoryGraph().get(1).getCategoryGraphNodes().get(0).getId());
    }
    
    protected void validateFilterByCat3000003(SearchResponse response) {
        assertEquals("cat3100004",  response.getCategoryGraph().get(0).getId());
        assertEquals("cat411000179",response.getCategoryGraph().get(0).getCategoryGraphNodes().get(0).getId());
        assertEquals("categoryPath:mycatalog.cat3000003.cat3100004.cat411000179", 
                response.getCategoryGraph().get(0).getCategoryGraphNodes().get(0).getPath());
        
        assertEquals("cat31000077", response.getCategoryGraph().get(1).getId());
        assertEquals("cat411000196",response.getCategoryGraph().get(1).getCategoryGraphNodes().get(0).getId());
        assertEquals("categoryPath:mycatalog.cat3000003.cat31000077.cat411000196", 
                response.getCategoryGraph().get(1).getCategoryGraphNodes().get(0).getPath());
    }
    
    protected void validateCategoryPathNotInFacets(SearchResponse response) {
        for(Facet facet :response.getFacets()){
            if(facet.getName().equals("categoryPath")) {
                fail("facet's shouldn't contain category path");
            }
        }
    }
    
    private void testSearchCategoryAux(SearchServer server, String term, String expectedProductId) throws SearchServerException {
        SolrQuery query = new SolrQuery(term);
        SearchResponse res = server.search(query, site);
        QueryResponse queryResponse = res.getQueryResponse();
        GroupResponse groupResponse = queryResponse.getGroupResponse();

        for (GroupCommand command : groupResponse.getValues()) {
            for (Group group : command.getValues()) {
                String productId = group.getGroupValue();
                if (expectedProductId.equals(productId)) {
                    return;
                }
            }
        }
        fail("Product TNF3137 not found");
    }

}