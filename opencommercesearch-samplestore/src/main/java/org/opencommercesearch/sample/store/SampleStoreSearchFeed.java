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
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.opencommercesearch.feed.SearchFeed;
import atg.commerce.inventory.InventoryException;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;

/**
 * @author S.L. (slisenkin at griddynamics dot com) 11.06.2013 17:01:36
 */
public class SampleStoreSearchFeed extends SearchFeed {

    
    public SampleStoreSearchFeed() {
    }
    
    @Override
    protected void onFeedStarted(long indexStamp) {
        // todo now sl: implement this
    }

    @Override
    protected void onDocumentsSent(UpdateResponse response, List<SolrInputDocument> documentList) {
        // todo now sl: implement this
    }

    @Override
    protected void onDocumentsSentError(List<SolrInputDocument> documentList) {
        // todo now sl: implement this
    }

    @Override
    protected void onFeedFinished(long indexStamp) {
        // todo now sl: implement this
    }

    @Override
    protected void processProduct(RepositoryItem product, Map<Locale, List<SolrInputDocument>> documents) throws RepositoryException,
            InventoryException {
        // todo now sl: implement this
    }

}
