package org.opencommercesearch;

/*
* Licensed to OpenCommerceSearch under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. OpenCommerceSearch licenses this
* file to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/

import atg.repository.RepositoryItem;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.RangeFacet;
import org.opencommercesearch.repository.FacetProperty;
import org.opencommercesearch.repository.FieldFacetProperty;
import org.opencommercesearch.repository.QueryFacetProperty;
import org.opencommercesearch.repository.RangeFacetProperty;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.hamcrest.collection.IsIterableContainingInOrder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class FacetManagerTest {

	private FacetManager manager = new FacetManager();
	
	@Mock
	RepositoryItem fieldFacet;
	
	@Mock
	RepositoryItem rangeFacet;
	
	@Mock
	RepositoryItem dateFacet;
	
	@Mock
	RepositoryItem queryFacet;
	
	@Mock
	SolrQuery query;
	
	@Before
	public void setUp() throws Exception {
		initMocks(this);
		
		when(fieldFacet.getPropertyValue(FacetProperty.TYPE)).thenReturn("fieldFacet");
        when(fieldFacet.getPropertyValue(FacetProperty.NAME)).thenReturn("fieldName");
		when(fieldFacet.getPropertyValue(FieldFacetProperty.FIELD)).thenReturn("fieldName");
		when(fieldFacet.getPropertyValue(FieldFacetProperty.IS_MULTI_SELECT)).thenReturn(true);
		when(fieldFacet.getPropertyValue(FieldFacetProperty.LIMIT)).thenReturn(100);
		when(fieldFacet.getPropertyValue(FieldFacetProperty.MIN_COUNT)).thenReturn(1);
		when(fieldFacet.getPropertyValue(FieldFacetProperty.SORT)).thenReturn("asc");
		when(fieldFacet.getPropertyValue(FieldFacetProperty.MISSING)).thenReturn(false);
		
		when(rangeFacet.getPropertyValue(FacetProperty.TYPE)).thenReturn("rangeFacet");
		when(rangeFacet.getPropertyValue(FacetProperty.NAME)).thenReturn("rangeName");
		when(rangeFacet.getPropertyValue(RangeFacetProperty.FIELD)).thenReturn("rangeName");
		when(rangeFacet.getPropertyValue(RangeFacetProperty.START)).thenReturn(0);
		when(rangeFacet.getPropertyValue(RangeFacetProperty.END)).thenReturn(1000);
		when(rangeFacet.getPropertyValue(RangeFacetProperty.GAP)).thenReturn(50);
		when(rangeFacet.getPropertyValue(RangeFacetProperty.IS_MULTI_SELECT)).thenReturn(true);
		when(rangeFacet.getPropertyValue(RangeFacetProperty.HARDENED)).thenReturn(false);
		
		
		when(queryFacet.getPropertyValue(FacetProperty.TYPE)).thenReturn("queryFacet");
        when(queryFacet.getPropertyValue(FacetProperty.NAME)).thenReturn("queryName");
		when(queryFacet.getPropertyValue(FieldFacetProperty.FIELD)).thenReturn("queryName");
		when(queryFacet.getPropertyValue(QueryFacetProperty.IS_MULTI_SELECT)).thenReturn(true);
		List<String> queries = new ArrayList<String>();
		queries.add("valQueryFacet");
		when(queryFacet.getPropertyValue(QueryFacetProperty.QUERIES)).thenReturn(queries);
		
		
		when(dateFacet.getPropertyValue(FacetProperty.TYPE)).thenReturn("dateFacet");
	}

	@Test
	public void testGetFacetItem() {
		addFacets();
		assertEquals(fieldFacet, manager.getFacetItem("fieldName"));
		assertEquals(rangeFacet, manager.getFacetItem("rangeName"));
        manager.setParams(query);
		verify(query).addFacetQuery("{!ex=queryName}queryName:valQueryFacet");		
	}
	
	@Test
	public void testGetFacetItemNoFacet() {
		assertNull(manager.getFacetItem("facet"));
	}

	@Test
	public void testAddFacet() {
		assertNull(manager.getFacetItem("fieldName"));
		manager.addFacet(fieldFacet);
		assertEquals(fieldFacet, manager.getFacetItem("fieldName"));
	}

	@Test
	public void testGetFacetNameString() {		
		addFacets();		
		assertEquals("facetName", manager.getFacetName("facetName"));
		assertEquals("rangeName", manager.getFacetName("rangeName"));
	}

	@Test
	public void testGetFacetNameFacetField() {
		addFacets();	
		FacetField facetField = new FacetField("facetName");
		assertEquals("facetName", manager.getFacetName(facetField));
		
		facetField = new FacetField("rangeName");
		assertEquals("rangeName", manager.getFacetName(facetField));
	}

	@Test
	public void testGetFacetNameRangeFacet() {
	    manager.addFacet(rangeFacet);
	    RangeFacet rangeFacetElement = mock(RangeFacet.class);
	    when(rangeFacetElement.getName()).thenReturn("rangeName");
	    assertEquals("rangeName", manager.getFacetName(rangeFacetElement));
	}

	@Test
	public void testGetCountNameCount() {
	    Count count = mock(Count.class);
	    when(count.getName()).thenReturn("MyBrand");
	    assertEquals("MyBrand", manager.getCountName(count));
	}

	@Test
	public void testGetCountNameRangeCount() {
	    RangeFacet.Count count = mock(RangeFacet.Count.class);
	    when(count.getValue()).thenReturn("val1");
	    assertEquals("val1", manager.getCountName(count));
	}

	@Test
	public void testGetCountPathFilterSelected() {
	    
	    Count count = mockCount("name");
	    mockFacetField(count, "facetName");	    
	    FilterQuery[] filterQueries = {mockFilterQuery("facetName", "name"), mockFilterQuery("facetName2", "name2")};
	    
	    assertEquals("filter_facetName2", manager.getCountPath(count, filterQueries));
	}
	
	@Test
    public void testGetCountPathFilterSelectedNoExtraFilter() {
        
        Count count = mockCount("name");
        mockFacetField(count, "facetName");     
        FilterQuery[] filterQueries = {mockFilterQuery("facetName", "name")};
        
        assertEquals("", manager.getCountPath(count, filterQueries));
    }
	
	@Test
    public void testGetCountPathCategoryFilterSelected() {        
        Count count = mockCount("1.catalog.root");
        mockFacetField(count, "category");     
        FilterQuery[] filterQueries = {mockFilterQuery("category", "1.catalog.root")};
        assertEquals("queryList", manager.getCountPath(count, filterQueries));
    }

	@Test
    public void testGetCountPathCategoryFilterSelectedWithPath() {        
        Count count = mockCount("1.catalog.root");
        mockFacetField(count, "category");     
        FilterQuery[] filterQueries = {mockFilterQuery("category", "1.catalog.root"), mockFilterQuery("filter2", "exp2")};
        assertEquals("filter_filter2|queryList", manager.getCountPath(count, filterQueries));
    }


	@Test
	public void testGetCountPathFilterSelected2() {
	    FilterQuery[] filterQueries = {mockFilterQuery("fieldName", "name")};
	    assertEquals("", manager.getCountPath("name", "fieldName", "filterQuery", filterQueries));
	}

	@Test
	public void testGetBreadCrumbs() {
	    FilterQuery[] filterQueries = {mockFilterQuery("fieldName", "exp"), mockFilterQuery("category", "1.catalog.root")};
	    List<BreadCrumb> crumbs = manager.getBreadCrumbs(filterQueries);
	    	    
	    assertEquals("root", crumbs.get(1).getExpression());
        assertEquals("category", crumbs.get(1).getFieldName());
        assertEquals("|filter_fieldName", crumbs.get(1).getPath());
        
        assertEquals("exp", crumbs.get(0).getExpression());
	    assertEquals("fieldName", crumbs.get(0).getFieldName());
	    assertEquals("filter_category", crumbs.get(0).getPath());
	}

    @Test
    public void testFacetWithDotInName() {
        Count count = mockCount("0.catalog.Category.X");
        assertEquals("Category.X", manager.getCountName(count, "0.catalog."));
    }

    @Test
    public void testFacetWithDotInNameNoPrefix() {
        Count count = mockCount("MyBrand.com");
        assertEquals("MyBrand.com", manager.getCountName(count));
    }
	
	@Test
    public void testGetBreadCrumbsNoFacet() {
	    List<BreadCrumb> crumbs = manager.getBreadCrumbs(null);
	    assertTrue(crumbs.isEmpty());
	}

    private FilterQuery mockFilterQuery(String fieldName, String expresion) {
        FilterQuery filterQuery = mock(FilterQuery.class);
        when(filterQuery.getFieldName()).thenReturn(fieldName);
        when(filterQuery.getExpression()).thenReturn(expresion);
        when(filterQuery.getUnescapeExpression()).thenReturn(expresion);
        when(filterQuery.toString()).thenReturn("filter_" + fieldName);
        return filterQuery;
    }

    private void mockFacetField(Count count, String facetName) {
        FacetField facetField = mock(FacetField.class);
        when(facetField.getName()).thenReturn(facetName);
        when(count.getFacetField()).thenReturn(facetField);
    }

    private Count mockCount(String name) {
        Count count = mock(Count.class);
        when(count.getName()).thenReturn(name);
        when(count.getAsFilterQuery()).thenReturn("queryList");
        return count;
    }
	
	private void addFacets(){		
		manager.addFacet(fieldFacet);
		manager.addFacet(rangeFacet);
		manager.addFacet(queryFacet);
		//TODO gsegura: uncomment when date facet is implemented
		//manager.addFacet(query, dateFacet);
	}


    @Test
    public void testFacetFieldNames() {
        FacetManager manager = new FacetManager();
        RepositoryItem colorFacet = mockFacetItem("facet1", "color");
        RepositoryItem brandFacet = mockFacetItem("facet2", "brand");
        RepositoryItem sizeFacet  = mockFacetItem("facet2", "size");
        manager.addFacet(colorFacet);
        manager.addFacet(brandFacet);
        manager.addFacet(sizeFacet);

        assertThat(manager.facetFieldNames(), IsIterableContainingInOrder.contains("color", "brand", "size"));
        assertEquals(colorFacet, manager.getFacetItem("color"));
        assertEquals(brandFacet, manager.getFacetItem("brand"));
        assertEquals(sizeFacet, manager.getFacetItem("size"));
    }

    @Test
    public void testFacetFieldNamesReplace() {
        FacetManager manager = new FacetManager();
        RepositoryItem colorFacet = mockFacetItem("facet1", "color");
        RepositoryItem brandFacet = mockFacetItem("facet2", "brand");
        RepositoryItem sizeFacet  = mockFacetItem("facet3", "size");

        manager.addFacet(colorFacet);
        manager.addFacet(brandFacet);
        manager.addFacet(sizeFacet);
        manager.clear();

        RepositoryItem priceFacet = mockFacetItem("facet4", "price");
        RepositoryItem genderFacet = mockFacetItem("facet5", "gender");
        RepositoryItem colorFacet2  = mockFacetItem("facet6", "color");

        manager.addFacet(priceFacet);
        manager.addFacet(genderFacet);
        manager.addFacet(colorFacet2);


        assertThat(manager.facetFieldNames(), IsIterableContainingInOrder.contains("price", "gender", "color"));
        assertEquals(priceFacet, manager.getFacetItem("price"));
        assertEquals(genderFacet, manager.getFacetItem("gender"));
        assertEquals(colorFacet2, manager.getFacetItem("color"));
    }

    private RepositoryItem mockFacetItem(String id, String fieldName) {
        RepositoryItem item = mock(RepositoryItem.class, id);
        when(item.getRepositoryId()).thenReturn(id);
        when(item.getPropertyValue(FacetProperty.FIELD)).thenReturn(fieldName);
        return item;
    }
}
