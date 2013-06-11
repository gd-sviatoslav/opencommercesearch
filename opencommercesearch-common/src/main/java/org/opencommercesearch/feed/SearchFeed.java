package org.opencommercesearch.feed;

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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.opencommercesearch.SearchServer;
import org.opencommercesearch.SearchServerException;
import org.opencommercesearch.repository.RuleBasedCategoryProperty;
import atg.commerce.inventory.InventoryException;
import atg.nucleus.GenericService;
import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryView;
import atg.repository.rql.RqlStatement;

/**
 * This class provides a basic functionality to generate a search feed. This includes:
 *  - Product loading
 *  - Category tokens
 *
 * @TODO implement default feed functionality
 */
@SuppressWarnings("static-method")
public abstract class SearchFeed extends GenericService {

    private SearchServer searchServer;
    private Repository productRepository;
    private String productItemDescriptorName;
    private RqlStatement productCountRql;
    private RqlStatement productRql;
    private int productBatchSize;
    private int indexBatchSize;

    public SearchServer getSearchServer() {
        return searchServer;
    }

    public void setSearchServer(SearchServer searchServer) {
        this.searchServer = searchServer;
    }

    public Repository getProductRepository() {
        return productRepository;
    }

    public void setProductRepository(Repository productRepository) {
        this.productRepository = productRepository;
    }

    public String getProductItemDescriptorName() {
        return productItemDescriptorName;
    }

    public void setProductItemDescriptorName(String productItemDescriptorName) {
        this.productItemDescriptorName = productItemDescriptorName;
    }

    public RqlStatement getProductCountRql() {
        return productCountRql;
    }

    public void setProductCountRql(RqlStatement productCountRql) {
        this.productCountRql = productCountRql;
    }

    public RqlStatement getProductRql() {
        return productRql;
    }

    public void setProductRql(RqlStatement productRql) {
        this.productRql = productRql;
    }

    public int getProductBatchSize() {
        return productBatchSize;
    }

    public void setProductBatchSize(int productBatchSize) {
        this.productBatchSize = productBatchSize;
    }

    public int getIndexBatchSize() {
        return indexBatchSize;
    }

    public void setIndexBatchSize(int indexBatchSize) {
        this.indexBatchSize = indexBatchSize;
    }

    public boolean isProductIndexable(RepositoryItem product) {
        return true;
    }

    public boolean isSkuIndexable(String sku) throws InventoryException {
        return true;
    }

    public boolean isCategoryIndexable(RepositoryItem category) {
        return true;
    }
 
    public void startFullFeed() throws SearchServerException, RepositoryException, SQLException,
            InventoryException {
        long startTime = System.currentTimeMillis();

        RepositoryView productView = getProductRepository().getView(getProductItemDescriptorName());
        int productCount = productRql.executeCountQuery(productView, null);

        if (isLoggingInfo()) {
            logInfo("Started full feed for " + productCount + " products");
        }

        long indexStamp = System.currentTimeMillis();
        int processedProductCount = 0;
        int indexedProductCount = 0;

        onFeedStarted(indexStamp);

        Integer[] rqlArgs = new Integer[] { 0, getProductBatchSize() };
        RepositoryItem[] products = productRql.executeQueryUncached(productView, rqlArgs);
        Map<Locale, List<SolrInputDocument>> documents = new HashMap<Locale, List<SolrInputDocument>>();

        while (products != null) {
            for (RepositoryItem product : products) {
                if (isProductIndexable(product)) {
                    processProduct(product, documents);
                    indexedProductCount++;
                    sendDocuments(documents, indexStamp, getIndexBatchSize());
                }
                processedProductCount++;
            }

            rqlArgs[0] += getProductBatchSize();
            products = productRql.executeQueryUncached(productView, rqlArgs);

            if (isLoggingInfo()) {
                logInfo("Processed " + processedProductCount  + " out of " + productCount);
                logInfo("Indexable products "+ indexedProductCount);
            }
        }

        sendDocuments(documents, indexStamp, 0);
        onFeedFinished(indexStamp);

        if (isLoggingInfo()) {
            logInfo("Full feed finished in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds, "
                    + indexedProductCount + " products were indexable from  " + processedProductCount
                    + " processed products");
        }
    }

    protected void sendDocuments(Map<Locale, List<SolrInputDocument>> documents, long indexStamp, int min) {
        for (Map.Entry<Locale, List<SolrInputDocument>> entry : documents.entrySet()) {
            List<SolrInputDocument> documentList = entry.getValue();

            if (documentList.size() > min) {
                for (SolrInputDocument doc : documentList) {
                    doc.setField("indexStamp", indexStamp);
                }

                try {
                    UpdateResponse response = getSearchServer().add(documentList, entry.getKey());
                    onDocumentsSent(response, documentList);
                    documentList.clear();
                } catch (SearchServerException ex) {
                    if (isLoggingError()) {
                        logError(ex);
                    }
                    onDocumentsSentError(documentList);
                }
            }
        }
    }

    protected abstract void onFeedStarted(long indexStamp);

    protected abstract void onDocumentsSent(UpdateResponse response, List<SolrInputDocument> documentList);

    protected abstract void onDocumentsSentError(List<SolrInputDocument> documentList);

    protected abstract void onFeedFinished(long indexStamp);

    protected abstract void processProduct(RepositoryItem product, Map<Locale, List<SolrInputDocument>> documents)
            throws RepositoryException, InventoryException;

    /**
     * Generate the category tokens to create a hierarchical facet in Solr. Each
     * token is formatted such that encodes the depth information for each node
     * that appears as part of the path, and include the hierarchy separated by
     * a common separator (depth/first level category name/second level
     * category name/etc)
     * 
     * @param document
     *            The document to set the attributes to.
     * @param product
     *            The RepositoryItem for the product item descriptor
     * @param catalogAssignments
     *            If the product is belongs to a category in any of those
     *            catalogs then that category is part of the returned value.
     */
    protected void loadCategoryPaths(SolrInputDocument document, RepositoryItem product,
            Set<RepositoryItem> catalogAssignments, Set<RepositoryItem> categoryCatalogs) {
        if (product != null) {
            try {
                @SuppressWarnings("unchecked")
                Set<RepositoryItem> productCategories = (Set<RepositoryItem>) product
                        .getPropertyValue("parentCategories");
                Set<String> tokenCache = new HashSet<String>();
                Set<String> ancestorCache = new HashSet<String>();
                Set<String> leaveCache = new HashSet<String>();
                
                if (productCategories != null) {
                    List<RepositoryItem> categoryIds = new ArrayList<RepositoryItem>();
                    for (RepositoryItem productCategory : productCategories) {
                        if (isCategoryInCatalogs(productCategory, catalogAssignments)) {
                            if (isRulesCategory(productCategory)) {
                                document.addField("ancestorCategoryId", productCategory.getRepositoryId());
                            } else if (isCategoryIndexable(productCategory)) {
                                loadCategoryPathsAndAncestorIds(document, productCategory, categoryIds, catalogAssignments, tokenCache, ancestorCache);
                            }

                            if (categoryCatalogs != null) {
                                @SuppressWarnings("unchecked")
                                Set<RepositoryItem> catalogs = (Set<RepositoryItem>) productCategory.getPropertyValue("catalogs");
                                for(RepositoryItem catalog : catalogs){
                                    if(catalogAssignments.contains(catalog)){
                                        categoryCatalogs.add(catalog);
                                    }
                                }
                            }
                            if(!isRulesCategory(productCategory)) {
                                leaveCache.add(productCategory.getItemDisplayName());
                            }
                        }
                    }
                    if(leaveCache.size() > 0) {
                        for(String leave : leaveCache) {
                            document.addField("categoryLeaves", leave);
                        }
                    }
                    
                    Set<String> nodeCache = new HashSet<String>();
                    
                    for(String token : tokenCache){
                        String[] splitToken = token.split("\\.");
                        if(splitToken != null && splitToken.length > 2) {
                            List<String> tokenList = Arrays.asList(splitToken);
                            tokenList = tokenList.subList(2, tokenList.size());
                            if (!tokenList.isEmpty()) {
                                nodeCache.addAll(tokenList);
                            }
                        }
                    }
                    
                    if(nodeCache.size() > 0) {
                        nodeCache.removeAll(leaveCache);
                        for(String node : nodeCache) {
                            document.addField("categoryNodes",  node);
                        }
                    }
                }
            } catch (Exception ex) {
                if (isLoggingError()) {
                    logError("Problem generating the categoryids attribute", ex);
                }
            }
        }
    }

    private boolean isRulesCategory(RepositoryItem category) throws RepositoryException {
    	if (category == null) {
    		return false;
    	}
    	return RuleBasedCategoryProperty.ITEM_DESCRIPTOR.equals(category.getItemDescriptor().getItemDescriptorName());
    }
    
    /**
     * Helper method to test if category is assigned to and of catalogs in the
     * given set
     * 
     * @param category
     *            the category to be tested
     * @param catalogs
     *            the set of categories to search in
     * @return
     */
    private boolean isCategoryInCatalogs(RepositoryItem category, Set<RepositoryItem> catalogs) {

        if (catalogs == null || catalogs.size() == 0) {
            return false;
        }
        
        boolean isAssigned = false;
        
        @SuppressWarnings("unchecked")
        Set<RepositoryItem> categoryCatalogs = (Set<RepositoryItem>) category.getPropertyValue("catalogs"); 
        if (categoryCatalogs != null) { 
            for (RepositoryItem categoryCatalog : categoryCatalogs) { 
                if (catalogs.contains(categoryCatalog)) { 
                    isAssigned = true;
                    break; 
                } 
            } 
        }
        
        return isAssigned;
    }

    /**
     * Helper method to generate the category tokens recursively
     * 
     * 
     * @param document
     *            The document to set the attributes to.
     * @param category
     *            The repositoryItem of the current level
     * @param hierarchyCategories
     *            The list where we store the categories during the recursion
     * @param catalogAssignments
     *            The list of catalogs to restrict the category token generation
     */
    private void loadCategoryPathsAndAncestorIds(SolrInputDocument document, RepositoryItem category,
            List<RepositoryItem> hierarchyCategories, Set<RepositoryItem> catalogAssignments, Set<String> tokenCache,
            Set<String> ancestorCache) {
        @SuppressWarnings("unchecked")
        Set<RepositoryItem> parentCategories = (Set<RepositoryItem>) category.getPropertyValue("fixedParentCategories");

        if (parentCategories != null && parentCategories.size() > 0) {
            hierarchyCategories.add(0, category);
            for (RepositoryItem parentCategory : parentCategories) {
                loadCategoryPathsAndAncestorIds(document, parentCategory, hierarchyCategories, catalogAssignments, tokenCache, ancestorCache);
            }
            hierarchyCategories.remove(0);
        } else {
            @SuppressWarnings("unchecked")
            Set<RepositoryItem> catalogs = (Set<RepositoryItem>) category.getPropertyValue("catalogs");
            for(RepositoryItem catalog : catalogs){
                if(catalogAssignments.contains(catalog)){
                    generateCategoryTokens(document, hierarchyCategories, catalog.getRepositoryId(), tokenCache);
                }
            }
        }
        if (!ancestorCache.contains(category.getRepositoryId())) {
            document.addField("ancestorCategoryId", category.getRepositoryId());
            ancestorCache.add(category.getRepositoryId());
        }
    }

    /**
     * Generates category tokens into a multivalued field called category. Each
     * token has the format: depth/catalog/category 1/,,,.categirt N, For
     * example:
     * 
     * 0/bcs 1/bcs/Men's Clothing 2/bcs/Men's Clothing/Men's Jackets 3/bcs/Men's
     * Clothing/Men's Jackets/Men's Casual Jacekt's
     * 
     * @param document
     *            The document to set the attributes to.
     * @param hierarchyCategories
     *
     * @param catalog
     *            
     */
    private void generateCategoryTokens(SolrInputDocument document, List<RepositoryItem> hierarchyCategories,
            String catalog, Set<String> tokenCache) {
        if (hierarchyCategories == null) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        StringBuilder builderIds = new StringBuilder();
        for (int i = 0; i <= hierarchyCategories.size(); i++) {
            builder.append(i).append(".").append(catalog).append(".");
            builderIds.append(catalog).append(".");
            
            for (int j = 0; j < i; j++) {
                builder.append(hierarchyCategories.get(j).getItemDisplayName()).append(".");
                builderIds.append(hierarchyCategories.get(j).getRepositoryId()).append(".");
            }
            builder.setLength(builder.length() - 1);
            builderIds.setLength(builderIds.length() - 1);

            String token = builder.toString();
            if (!tokenCache.contains(token)) {
                document.addField("category", builder.toString());
                document.addField("categoryPath", builderIds.toString());
                tokenCache.add(token);
            }
            builder.setLength(0);
            builderIds.setLength(0);
        }
    }
    
}
