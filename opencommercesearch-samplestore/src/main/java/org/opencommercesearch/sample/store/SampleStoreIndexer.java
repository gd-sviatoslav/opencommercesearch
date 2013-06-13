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
package org.opencommercesearch.sample.store;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.opencommercesearch.feed.SearchFeed;
import atg.commerce.inventory.InventoryException;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryItemDescriptor;
import com.google.common.collect.Lists;

/**
 * @author S.L. (slisenkin at griddynamics dot com) 11.06.2013 17:01:36
 */
public class SampleStoreIndexer extends SearchFeed {

    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    
    public SampleStoreIndexer() {
    }
    
    @Override
    protected void onFeedStarted(long indexStamp) {
        ATGLoggingUtil.info(this, "Feed import started, indexStamp=[{0}].", indexStamp);
    }

    @Override
    protected void onDocumentsSent(UpdateResponse response, List<SolrInputDocument> documentList) {
        ATGLoggingUtil.info(this, "Feed import, sent [{0}] documents.", documentList.size());
    }

    @Override
    protected void onDocumentsSentError(List<SolrInputDocument> documentList) {
        // nothing
    }

    @Override
    protected void onFeedFinished(long indexStamp) {
        ATGLoggingUtil.info(this, "Feed import finished, indexStamp=[{0}].", indexStamp);
    }

    /**
     * Calls {@link #loadCategoryPaths(SolrInputDocument, RepositoryItem, Set, Set)} with catalogAssignments from the product and 'null' as categoryCatalogs. 
     */
    protected void loadCategoryPaths(SolrInputDocument document, RepositoryItem product) {        
        @SuppressWarnings("unchecked")
        Set<RepositoryItem> catalogAssignments = (Set<RepositoryItem>) product.getPropertyValue("catalogs");
        super.loadCategoryPaths(document, product, catalogAssignments, null);
    }
    
    @Override
    protected void processProduct(RepositoryItem product, Map<Locale, List<SolrInputDocument>> documents) throws RepositoryException,
            InventoryException {
        List<SolrInputDocument> docs = documents.get(DEFAULT_LOCALE);
        if(null == docs){
            docs = Lists.newArrayList();
            documents.put(DEFAULT_LOCALE, docs);
        }
        SolrInputDocument doc = new SolrInputDocument();

        // todo now sl: load fields by schema configuration
        RepositoryItemDescriptor meta = product.getItemDescriptor();
        String[] propNames = meta.getPropertyNames();
        for (String name : propNames) {
            doc.setField(name, product.getPropertyValue(name));
        }
 
        loadCategoryPaths(doc, product);
        
        docs.add(doc);
    }

}
