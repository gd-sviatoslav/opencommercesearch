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

import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryView;
import atg.repository.rql.RqlStatement;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.impl.LBHttpSolrServer;
import org.apache.solr.common.cloud.*;
import org.apache.solr.common.util.NamedList;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.opencommercesearch.repository.SearchRepositoryItemDescriptor;
import org.opencommercesearch.repository.SynonymListProperty;
import org.opencommercesearch.repository.SynonymProperty;

import java.io.InputStream;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class CloudSearchServerUnitTest {

    @Mock
    private SolrZkClient zkClient;

    @Mock
    private ZkStateReader zkStatereader;

    @Mock
    private ClusterState clusterState;

    @Mock
    private Slice slice1;

    @Mock
    private Slice slice2;

    @Mock
    private Replica replica1;

    @Mock
    private Replica replica2;

    @Mock
    private CloudSolrServer catalogSolrServer;

    @Mock
    private CloudSolrServer rulesSolrServer;

    @Mock
    private LBHttpSolrServer lbHttpSolrServer;

    @Mock
    private HttpClient httpClient;

    @Mock
    private StatusLine httpStatusLine;

    @Mock
    private HttpResponse httpResponse;

    @Mock
    private HttpEntity httpEntity;

    @Mock
    private InputStream httpInputStream;

    @Mock
    private ResponseParser responseParser;

    @Mock
    private Repository searchRepository;

    @Mock
    private RepositoryView synonymListRepositoryView;
    
    @Mock
    private RepositoryView synonymRepositoryView;

    @Mock
    private RqlStatement synonymListRql;

    @Mock
    private RqlStatement synonymRql;
    
    @Mock
    private SolrZkClient solrZkClient;

    private CloudSearchServer cloudSearchServer;

    private Locale getLocale() {
        return Locale.ENGLISH;
    }
    
    @Before
    public void setUp() throws Exception {
        initMocks(this);
        cloudSearchServer = new CloudSearchServer() {
            @Override
            public void logInfo(String s, Throwable t) {
            }
        };
        cloudSearchServer.setRulesCollection("rules");
        cloudSearchServer.setCatalogCollection("catalog");
        cloudSearchServer.setCatalogConfig("product_catalog_conf");
        cloudSearchServer.setRulesConfig("rules_conf");
        cloudSearchServer.setCatalogSolrServer(catalogSolrServer, getLocale());
        cloudSearchServer.setRulesSolrServer(rulesSolrServer, getLocale());
        cloudSearchServer.setLoggingInfo(true);
        cloudSearchServer.setLoggingError(true);
        cloudSearchServer.setLoggingError(true);
        cloudSearchServer.setLoggingWarning(true);
        cloudSearchServer.setLoggingTrace(true);
        cloudSearchServer.setResponseParser(responseParser);
        cloudSearchServer.setSearchRepository(searchRepository);
        cloudSearchServer.setSynonymListRql(synonymListRql);
        cloudSearchServer.setSynonymRql(synonymRql);
        
        initZkMocks();
        initHttpMocks();
        
        when(searchRepository.getView(SearchRepositoryItemDescriptor.SYNONYM_LIST)).thenReturn(synonymListRepositoryView);
        when(searchRepository.getView(SearchRepositoryItemDescriptor.SYNONYM)).thenReturn(synonymRepositoryView);
        when(responseParser.processResponse(eq(httpInputStream), anyString())).thenReturn(new NamedList<Object>());
    }

    private void initZkMocks() throws KeeperException, InterruptedException {
        when(zkClient.exists(anyString(), anyBoolean())).thenReturn(false);
        when(zkStatereader.getClusterState()).thenReturn(clusterState);
        Set<String> liveNodes = new HashSet<String>();
        liveNodes.add("nodeName1");
        liveNodes.add("nodeName2");
        when(clusterState.getLiveNodes()).thenReturn(liveNodes);

        Map<String, Slice> slices = new HashMap<String, Slice>();
        slices.put("slice1", slice1);
        slices.put("slice2", slice2);
        when(clusterState.getSlicesMap(cloudSearchServer.getRulesCollection(getLocale()))).thenReturn(slices);
        when(clusterState.getSlicesMap(cloudSearchServer.getCatalogCollection(getLocale()))).thenReturn(slices);

        Collection<Replica> replicas = Arrays.asList(replica1, replica2);
        when(slice1.getReplicas()).thenReturn(replicas);

        when(replica1.getStr(ZkStateReader.NODE_NAME_PROP)).thenReturn("nodeName1");
        when(replica2.getStr(ZkStateReader.NODE_NAME_PROP)).thenReturn("nodeName2");
        when(replica1.getStr(ZkStateReader.STATE_PROP)).thenReturn(ZkStateReader.ACTIVE);
        when(replica2.getStr(ZkStateReader.STATE_PROP)).thenReturn(ZkStateReader.DOWN);
        when(replica1.getStr(ZkStateReader.BASE_URL_PROP)).thenReturn("http://node1.opencommercesearch.org");
        when(replica2.getStr(ZkStateReader.BASE_URL_PROP)).thenReturn("http://node2.opencommercesearch.org");
        when(replica1.getStr(ZkStateReader.CORE_NAME_PROP)).thenReturn("mycore");
        when(replica2.getStr(ZkStateReader.CORE_NAME_PROP)).thenReturn("mycore");

        when(catalogSolrServer.getZkStateReader()).thenReturn(zkStatereader);
        when(catalogSolrServer.getLbServer()).thenReturn(lbHttpSolrServer);
        when(rulesSolrServer.getZkStateReader()).thenReturn(zkStatereader);
        when(rulesSolrServer.getLbServer()).thenReturn(lbHttpSolrServer);
        when(lbHttpSolrServer.getHttpClient()).thenReturn(httpClient);
    }

    private void initHttpMocks() throws Exception {
        when(httpClient.execute(any(HttpRequestBase.class))).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(httpStatusLine);
        when(httpStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(httpResponse.getEntity()).thenReturn(httpEntity);
        when(httpEntity.getContent()).thenReturn(httpInputStream);
    }

    @Test
    public void testExportSynonymsNewFile() throws Exception {
        
        initExportSynonyms(zkClient);
        cloudSearchServer.exportSynonyms(getLocale());
        
        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(zkClient, times(2)).makePath(pathCaptor.capture(), dataCaptor.capture(), any(CreateMode.class), eq(true));
        
        verifySynonymExport(dataCaptor, pathCaptor);
    }

    @Test
    public void testExportSynonymsExistingFile() throws Exception {

        initExportSynonyms(zkClient);
        when(zkClient.exists(anyString(), anyBoolean())).thenReturn(true);

        cloudSearchServer.exportSynonyms(getLocale());
        
        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(zkClient, times(2)).setData(pathCaptor.capture(), dataCaptor.capture(), eq(true));
        
        verifySynonymExport(dataCaptor, pathCaptor);
    }

    @Test
    public void testExportSynonymZeroLists() throws Exception {
        when(synonymListRql.executeQuery(synonymListRepositoryView, null)).thenReturn(null);
        when(synonymRql.executeQuery(synonymRepositoryView, null)).thenReturn(null);
        
        cloudSearchServer.exportSynonyms(getLocale());

        verifyZeroInteractions(zkClient);
    }

    private void verifySynonymExport(ArgumentCaptor<byte[]> dataCaptor, ArgumentCaptor<String> pathCaptor) {
        assertNotNull(dataCaptor.getAllValues());
        byte[] capturedData = dataCaptor.getAllValues().get(0);
        String capturedDataStr = new String(capturedData);
        
        assertTrue(capturedDataStr.contains("synonym1 > mapping1"));
        assertTrue(capturedDataStr.contains("synonym2 > mapping2"));
        
        assertTrue(pathCaptor.getAllValues().get(0).contains("/configs/product_catalog_conf/synonyms-preview/index_synonyms.txt"));
        assertTrue(pathCaptor.getAllValues().get(1).contains("/configs/rules_conf/synonyms-preview/index_synonyms.txt"));
    }

    private RepositoryItem initExportSynonyms(SolrZkClient zkClient) throws SearchServerException, RepositoryException {
        cloudSearchServer.setSolrZkClient(zkClient);
        
        RepositoryItem synonymList = mock(RepositoryItem.class);
        when(synonymList.getRepositoryId()).thenReturn("synLst01");
        when(synonymList.getItemDisplayName()).thenReturn("synonymList");
        when(synonymList.getPropertyValue(SynonymListProperty.FILENAME)).thenReturn("index_synonyms");
        when(synonymListRql.executeQuery(synonymListRepositoryView, null)).thenReturn(new RepositoryItem[]{synonymList});
        
        RepositoryItem synonym1 = mockMapping(synonymList, "synonym1 > mapping1");
        RepositoryItem synonym2 = mockMapping(synonymList, "synonym2 > mapping2");
        
        when(synonymRql.executeQuery(eq(synonymRepositoryView), any(Object[].class))).thenReturn(new RepositoryItem[]{synonym1, synonym2});

        cloudSearchServer.setCatalogCollection("catalogCollection");
        cloudSearchServer.setRulesCollection("ruleCollection");
        
        return synonymList;
    }
    
    private RepositoryItem mockMapping(RepositoryItem synonymList, String mappingStr){
        RepositoryItem synonym = mock(RepositoryItem.class);
        when(synonym.getPropertyValue(SynonymProperty.SYNONYM_LIST)).thenReturn(synonymList);
        when(synonym.getPropertyValue(SynonymProperty.MAPPING)).thenReturn(mappingStr);
        return synonym;
                
    }
    
    @Test
    public void testReloadCollections() throws Exception {
       cloudSearchServer.reloadCollections();

        ArgumentCaptor<HttpRequestBase> argument = ArgumentCaptor.forClass(HttpRequestBase.class);
        verify(httpClient, times(2)).execute(argument.capture());
        List<String> requestUrls = new ArrayList<String>(argument.getAllValues().size());
        for (HttpRequestBase request : argument.getAllValues()) {
            requestUrls.add(request.getURI().toString());
        }
        assertThat(requestUrls, containsInAnyOrder(
            "http://node1.opencommercesearch.org/admin/cores?action=RELOAD&core=" + cloudSearchServer.getCatalogCollection(getLocale()) + "&indexInfo=true",
            "http://node1.opencommercesearch.org/admin/cores?action=RELOAD&core=" + cloudSearchServer.getRulesCollection(getLocale()) + "&indexInfo=true"
        ));
    }

    @Test
    public void testReloadCollectionsNoLiveNodes() throws Exception {
        when(clusterState.getLiveNodes()).thenReturn(null);
        cloudSearchServer.reloadCollections();

        verifyZeroInteractions(httpClient);
    }

    @Test
    public void testInitSolrServer() throws Exception {
        CloudSearchServer server = new CloudSearchServer();

        server.setCatalogCollection(cloudSearchServer.getCatalogCollection());
        server.setRulesCollection(cloudSearchServer.getRulesCollection());

        assertNull(server.getCatalogSolrServer(getLocale()));
        assertNull(server.getRulesSolrServer(getLocale()));

        server.initSolrServer();

        assertNotNull(server.getCatalogSolrServer(getLocale()));
        assertNotNull(server.getRulesSolrServer(getLocale()));
    }

}
