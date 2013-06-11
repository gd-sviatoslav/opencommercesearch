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

import static org.opencommercesearch.repository.RankingRuleProperty.ATTRIBUTE;
import static org.opencommercesearch.repository.RankingRuleProperty.BOOST_BY;
import static org.opencommercesearch.repository.RankingRuleProperty.BOOST_BY_FACTOR;
import static org.opencommercesearch.repository.RankingRuleProperty.STRENGTH;
import static org.opencommercesearch.repository.RankingRuleProperty.STRENGTH_LEVELS;
import static org.opencommercesearch.repository.RankingRuleProperty.STRENGTH_MAXIMUM_BOOST;
import static org.opencommercesearch.repository.RankingRuleProperty.STRENGTH_MAXIMUM_DEMOTE;
import static org.opencommercesearch.repository.RankingRuleProperty.STRENGTH_MEDIUM_BOOST;
import static org.opencommercesearch.repository.RankingRuleProperty.STRENGTH_MEDIUM_DEMOTE;
import static org.opencommercesearch.repository.RankingRuleProperty.STRENGTH_NEUTRAL;
import static org.opencommercesearch.repository.RankingRuleProperty.STRENGTH_STRONG_BOOST;
import static org.opencommercesearch.repository.RankingRuleProperty.STRENGTH_STRONG_DEMOTE;
import static org.opencommercesearch.repository.RankingRuleProperty.STRENGTH_WEAK_BOOST;
import static org.opencommercesearch.repository.RankingRuleProperty.STRENGTH_WEAK_DEMOTE;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.opencommercesearch.repository.BlockRuleProperty;
import org.opencommercesearch.repository.BoostRuleProperty;
import org.opencommercesearch.repository.CategoryProperty;
import org.opencommercesearch.repository.FacetProperty;
import org.opencommercesearch.repository.FacetRuleProperty;
import org.opencommercesearch.repository.RuleProperty;
import org.opencommercesearch.repository.SearchRepositoryItemDescriptor;
import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;

/**
 * This class provides functionality to load the rules that matches a given
 * query or triggers.
 * 
 * @author rmerizalde
 *
 * @todo decouple this class from ATG
 */
@SuppressWarnings("static-method")
public class RuleManager<T extends SolrServer> {
    public static final String FIELD_CATEGORY = "category";
    public static final String FIELD_ID = "id";
    public static final String FIELD_BOOST_FUNCTION = "boostFunction";
    public static final String FIELD_FACET_FIELD = "facetField";
    public static final String FIELD_SORT_PRIORITY = "sortPriority";
    public static final String FIELD_COMBINE_MODE = "combineMode";
    public static final String FIELD_QUERY = "query";
    public static final String FIELD_SCORE = "score";

    private static final String WILDCARD = "__all__";

    private Repository searchRepository;
    private RulesBuilder rulesBuilder;
    private SolrServer server;
    private FacetManager facetManager = new FacetManager();
    private Map<String, List<RepositoryItem>> rules;
    private Map<String, SolrDocument> ruleDocs;
    private Map<String, String> strengthMap;

    enum RuleType {
        facetRule() {
            void setParams(RuleManager<?> manager, SolrQuery query, List<RepositoryItem> rules, Map<String, SolrDocument> ruleDocs) {
                if (rules == null || rules.size() == 0) {
                    return;
                }

                FacetManager facetManager = manager.getFacetManager();

                for (RepositoryItem rule : rules) {
                    SolrDocument doc = ruleDocs.get(rule.getRepositoryId());

                    if (FacetRuleProperty.COMBINE_MODE_REPLACE.equals(doc.getFieldValue(FacetRuleProperty.COMBINE_MODE))) {
                        facetManager.clear();
                    }

                    @SuppressWarnings("unchecked")
                    List<RepositoryItem> facets = (List<RepositoryItem>) rule.getPropertyValue(FacetRuleProperty.FACETS);
                    if (facets != null) {
                        for (RepositoryItem facet : facets) {
                            facetManager.addFacet(facet);
                        }
                    }
                }
                facetManager.setParams(query);
            }
        },
        boostRule() {
            @SuppressWarnings("deprecation")
            void setParams(RuleManager<?> manager, SolrQuery query, List<RepositoryItem> rules, Map<String, SolrDocument> ruleDocs) {
                String[] sortFields = query.getSortFields();
                if (sortFields != null && sortFields.length > 1) {
                    // user has selected a sorting option, ignore manual boosts
                    return;
                }

                for (RepositoryItem rule : rules) {
                    @SuppressWarnings("unchecked")
                    List<RepositoryItem> products = (List<RepositoryItem>) rule
                            .getPropertyValue(BoostRuleProperty.BOOSTED_PRODUCTS);
                    if (products != null && products.size() > 0) {
                        StringBuffer b = new StringBuffer("fixedBoost(productId,");
                        for (RepositoryItem product : products) {
                            b.append("'").append(product.getRepositoryId()).append("',");
                        }
                        b.setLength(b.length() - 1);
                        b.append(")");
                        query.addSortField(b.toString(), ORDER.asc);
                    }

                    // @todo handle multiple boost rules
                    break;
                }

            }
        },
        blockRule() {
            void setParams(RuleManager<?> manager, SolrQuery query, List<RepositoryItem> rules, Map<String, SolrDocument> ruleDocs) {

                for (RepositoryItem rule : rules) {
                    @SuppressWarnings("unchecked")
                    Set<RepositoryItem> products = (Set<RepositoryItem>) rule
                            .getPropertyValue(BlockRuleProperty.BLOCKED_PRODUCTS);

                    if (products != null) {
                        for (RepositoryItem product : products) {
                            query.addFilterQuery("-productId:" + product.getRepositoryId());
                        }
                    }
                }

            }
        },
        redirectRule() {

            @Override
            void setParams(RuleManager<?> manager, SolrQuery query, List<RepositoryItem> rules, Map<String, SolrDocument> ruleDocs) {
                //TODO gsegura: for redirect rule we don't need a enum entry to add parameters to the query
                //but to avoid an exception while on:  RuleType.valueOf(entry.getKey());  we are adding
                //this empty entry. The redirect itself will be handled by the abstractSearchServer
            }
            
        },
        rankingRule() {
            @Override
            void setParams(RuleManager<?> manager, SolrQuery query, List<RepositoryItem> rules, Map<String, SolrDocument> ruleDocs) {
                for (RepositoryItem rule : rules) {
                    SolrDocument doc = ruleDocs.get(rule.getRepositoryId());

                    if (doc != null) {
                        String boostFunction = (String) doc.getFieldValue(FIELD_BOOST_FUNCTION);

                        if (boostFunction != null) {
                            query.add("boost", boostFunction);
                        }
                    }
                }
            }
        };
        
        abstract void setParams(RuleManager<?> manager, SolrQuery query, List<RepositoryItem> rules, Map<String, SolrDocument> ruleDocs);
    }

    RuleManager(Repository searchRepository, RulesBuilder rulesBuilder, T server) {
      this.searchRepository = searchRepository;
      this.rulesBuilder = rulesBuilder;
      this.server = server;
    }
    
    public FacetManager getFacetManager() {
        return facetManager;
    }

    public Map<String, List<RepositoryItem>> getRules() {
        return rules;
    }


    /**
     * Loads the rules that matches the given query
     * 
     * @param q
     *            is the user query
     * @param isSearch
     *            indicate if we are browsing or searching the site
     * @param catalog
     *            the current catalog we are browsing/searching
     * @throws RepositoryException
     *             if an exception happens retrieving a rule from the repository
     * @throws SolrServerException
     *             if an exception happens querying the search engine
     */
    @SuppressWarnings("deprecation")
    void loadRules(String q, String categoryFilterQuery, boolean isSearch, RepositoryItem catalog) throws RepositoryException,
            SolrServerException {
        if (isSearch && StringUtils.isBlank(q)) {
            throw new IllegalArgumentException("Missing query ");
        }
        StringBuilder queryStr = new StringBuilder();
        if (isSearch) {
            queryStr.append("(target:allpages OR target:searchpages) AND ((");
            queryStr.append(q);
            queryStr.append(")^2 OR query:__all__)");
        } else {
            queryStr.append("(target:allpages OR target:categorypages)");
        }
        
        SolrQuery query = new SolrQuery(queryStr.toString());
        int start = 0;
        int rows = 20;
        query.setStart(start);
        query.setRows(rows);
        query.setParam("fl", "id");
        query.addSortField(FIELD_SORT_PRIORITY, ORDER.asc);
        query.addSortField(FIELD_SCORE, ORDER.asc);
        query.addSortField(FIELD_ID, ORDER.asc);
        query.add("fl", FIELD_BOOST_FUNCTION, FIELD_FACET_FIELD, FIELD_COMBINE_MODE, FIELD_QUERY);

        StringBuffer filterQueries = new StringBuffer().append("(category:").append(WILDCARD);
        if (StringUtils.isNotBlank(categoryFilterQuery)) {
            filterQueries.append(" OR ").append("category:" + categoryFilterQuery);

        }
                
        filterQueries.append(") AND ").append("(siteId:").append(WILDCARD);
        @SuppressWarnings("unchecked")
        Set<String> siteSet = (Set<String>) catalog.getPropertyValue("siteIds");
        if (siteSet != null) {
            for(String site : siteSet) {
                filterQueries.append(" OR ").append("siteId:" + site);
            }
        }
                
        filterQueries.append(") AND ").append("(catalogId:").append(WILDCARD).append(" OR ").append("catalogId:")
        .append(catalog.getRepositoryId()).append(")");
        
        query.addFilterQuery(filterQueries.toString());
        QueryResponse res = server.query(query);
        
        if (res.getResults() == null || res.getResults().getNumFound() == 0) {
            rules = Collections.emptyMap();
            return;
        }

        rules = new HashMap<String, List<RepositoryItem>>(RuleType.values().length);
        ruleDocs = new HashMap<String, SolrDocument>();
        SolrDocumentList docs = res.getResults();
        int total = (int) docs.getNumFound();
        int processed = 0;
        while (processed < total) {
            for (int i = 0; i < docs.size(); i++) {
                ++processed;
                SolrDocument doc = docs.get(i);

                if (isSearch && !matchesQuery(q, doc)) {
                    // skip this rule
                    continue;
                }

                RepositoryItem rule = searchRepository.getItem((String) doc.getFieldValue("id"),
                        SearchRepositoryItemDescriptor.RULE);
                if (rule != null) {
                    String ruleType = (String) rule.getPropertyValue(RuleProperty.RULE_TYPE);
                    List<RepositoryItem> ruleList = (List<RepositoryItem>) rules.get(ruleType);
                    if (ruleList == null) {
                        ruleList = new ArrayList<RepositoryItem>();
                        rules.put(ruleType, ruleList);
                    }
                    ruleList.add(rule);
                    ruleDocs.put(rule.getRepositoryId(), doc);
                } else {
                    //TODO gsegura: add logging that we couldn't find the rule item in the DB
                }
            }
            if (processed < total) {
                query.setStart(processed);
                res = server.query(query);
                docs = res.getResults();
            }
        }
    }

    /**
     * Returns true if the given rule was configured as an exact match and the query q matches the query in the rule
     */
    private boolean matchesQuery(String q, SolrDocument rule) {
        String targetQuery = (String) rule.getFieldValue(FIELD_QUERY);

        if (isExactMatch(targetQuery)) {
            targetQuery = removeBrackets(targetQuery).toLowerCase();
            return targetQuery.equals(q.toLowerCase());
        }
        return true;
    }

    /**
     * Return true if the given query is an exact match, owtherwise false. The exact match syntax is to put the query
     * in the rule between brackets. For example ("the bike").
     */    
    private boolean isExactMatch(String query) {
        return query != null && query.startsWith("[") && query.endsWith("]");
    }

    /**
     * Just a helper method to strip off the characters
     */
    private String removeBrackets(String query) {
        return query.substring(1, query.length() - 1);
    }

    void setRuleParams(FilterQuery[] filterQueries, RepositoryItem catalog, SolrQuery query, boolean isSearch)
            throws RepositoryException,
            SolrServerException {
        if (getRules() == null) {
            String categoryFilterQuery = extractCategoryFilterQuery(filterQueries);
            loadRules(query.getQuery(), categoryFilterQuery, isSearch, catalog);
        }
        setRuleParams(query, getRules());
        setFilterQueries(filterQueries, catalog.getRepositoryId(), query);
    }

    // Helper method to process the rules for this request
    void setRuleParams(SolrQuery query, Map<String, List<RepositoryItem>> rules) {
        setRuleParams(query, rules, ruleDocs);
    }

    // Helper method to process the rules for this request
    @SuppressWarnings("deprecation")
    void setRuleParams(SolrQuery query, Map<String, List<RepositoryItem>> rules, Map<String, SolrDocument> ruleDocs) {
        if (rules == null) {
            return;
        }

        String[] sortFields = query.getSortFields();

        // always push the products out of stock to the bottom, even when manual boosts have been selected
        query.setSortField("isToos", ORDER.asc);

        // add sort specs after the isToos and possibly
        if (sortFields != null) {
            Set<String> sortFieldSet = new HashSet<String>(sortFields.length);

            for (String sortField : sortFields) {
                String[] parts = StringUtils.split(sortField, ' ');
                String fieldName = parts[0];
                String order = parts[1];

                if (!("score".equals(fieldName) || sortFieldSet.contains(fieldName))) {
                    query.addSortField(fieldName, ORDER.valueOf(order));
                    sortFieldSet.add(fieldName);
                }
            }
        }

        for (Entry<String, List<RepositoryItem>> entry : rules.entrySet()) {
            RuleType type = RuleType.valueOf(entry.getKey());
            
            if (type != null) {
                type.setParams(this, query, entry.getValue(), ruleDocs);
            }
        }

        // finally add the score field to the sorting spec. Score will be a tie breaker when other sort specs are added
        query.addSortField("score", ORDER.desc);
    }

    private void setFilterQueries(FilterQuery[] filterQueries, String catalogId, SolrQuery query) {
        query.setFacetPrefix("category", "1." + catalogId + ".");
        query.addFilterQuery("category:" + "0." + catalogId);

        if (filterQueries == null) {
            return;
        }
        
        Map<String, Set<String>> multiExpressionFilters = new HashMap<String, Set<String>>();

        for (FilterQuery filterQuery : filterQueries) {
            if (filterQuery.getFieldName().equals("category")) {
                String category = filterQuery.getExpression();
                int index = category.indexOf(SearchConstants.CATEGORY_SEPARATOR);
                if (index != -1) {
                    int level = Integer.parseInt(category.substring(0, index));

                    category = ++level + FilterQuery.unescapeQueryChars(category.substring(index)) + ".";
                    query.setFacetPrefix("category", category);
                }
            }
            RepositoryItem facetItem = getFacetManager().getFacetItem(filterQuery.getFieldName());
            if (facetItem != null) {
                Boolean isMultiSelect = (Boolean) facetItem.getPropertyValue(FacetProperty.IS_MULTI_SELECT);
                if (isMultiSelect != null && isMultiSelect) {
                    //query.addFilterQuery( +  + + filterQuery);
                    Set<String> expressions = multiExpressionFilters.get(filterQuery.getFieldName());
                    if (expressions == null) {
                        expressions = new HashSet<String>();
                        multiExpressionFilters.put(filterQuery.getFieldName(), expressions);
                    }
                    expressions.add(filterQuery.getExpression());
                    continue;
                }
            }
            query.addFilterQuery(filterQuery.toString());
        }

        StringBuffer b = new StringBuffer();
        for (Entry<String, Set<String>> entry : multiExpressionFilters.entrySet()) {
            String operator = " OR ";
            String fieldName = entry.getKey();
            b.append("{!tag=").append(fieldName).append("}");
            for (String expression : entry.getValue()) {
                b.append(fieldName).append(FilterQuery.SEPARATOR).append(expression).append(operator);
            }
            b.setLength(b.length() - operator.length());
            query.addFilterQuery(b.toString());
            b.setLength(0);
        }
    }

    private String extractCategoryFilterQuery(FilterQuery[] filterQueries) {
        if (filterQueries == null) {
            return null;
        }

        for (FilterQuery filterQuery : filterQueries) {
            if (filterQuery.getFieldName().equals("category")) {
                return filterQuery.getExpression();
            }
        }

        return null;
    }

    /**
     * Create a search document representing a rule
     * 
     * @param rule
     *            the repository item to be indexed
     * @return the search document to be indexed
     * @throws RepositoryException
     *             is an exception occurs while retrieving data from the
     *             repository
     */
    SolrInputDocument createRuleDocument(RepositoryItem rule) throws RepositoryException {
        SolrInputDocument doc = new SolrInputDocument();
        doc.setField("id", rule.getRepositoryId());
        String query = (String) rule.getPropertyValue(RuleProperty.QUERY);
        if (query == null || query.equals("*")) {
            query = WILDCARD;
        } else {
            query = query.toLowerCase();
        }
        doc.setField(FIELD_QUERY, query);
        doc.setField(FIELD_SORT_PRIORITY, rule.getPropertyValue(RuleProperty.SORT_PRIORITY));
        doc.setField(FIELD_COMBINE_MODE, rule.getPropertyValue(RuleProperty.COMBINE_MODE));
        
        String target = (String) rule.getPropertyValue(RuleProperty.TARGET);
        if(target != null) {
            target = StringUtils.replace(target, " ", "");
            doc.setField("target",target.toLowerCase());
        }
        
        @SuppressWarnings("unchecked")
        Set<RepositoryItem> sites = (Set<RepositoryItem>) rule.getPropertyValue(RuleProperty.SITES);

        if (sites != null && sites.size() > 0) {
            for (RepositoryItem site : sites) {
                doc.addField("siteId", site.getRepositoryId());
            }
        } else {
            doc.setField("siteId", WILDCARD);
        }

        @SuppressWarnings("unchecked")
        Set<RepositoryItem> catalogs = (Set<RepositoryItem>) rule.getPropertyValue(RuleProperty.CATALOGS);

        if (catalogs != null && catalogs.size() > 0) {
            for (RepositoryItem catalog : catalogs) {
                doc.addField("catalogId", catalog.getRepositoryId());
            }
        } else {
            doc.setField("catalogId", WILDCARD);
        }

        @SuppressWarnings("unchecked")
        Set<RepositoryItem> categories = (Set<RepositoryItem>) rule.getPropertyValue(RuleProperty.CATEGORIES);


        if (categories != null && categories.size() > 0) {
            Boolean includeSubcategories = (Boolean) rule.getPropertyValue(RuleProperty.INCLUDE_SUBCATEGORIES);

            if (includeSubcategories == null) {
                includeSubcategories = Boolean.FALSE;
            }

            for (RepositoryItem category : categories) {
                setCategorySearchTokens(doc, category, includeSubcategories);
            }
        } else {
            doc.setField(FIELD_CATEGORY, WILDCARD);
        }

        String ruleType = (String) rule.getPropertyValue(RuleProperty.RULE_TYPE);
        if (RuleProperty.TYPE_RANKING_RULE.equals(ruleType)) {
            setRankingRuleFields(rule, doc);
        } else if (RuleProperty.TYPE_FACET_RULE.equals(ruleType)) {
            setFacetRuleFields(rule, doc);
        }

        return doc;
    }

    private void setFacetRuleFields(RepositoryItem rule, SolrInputDocument doc) {
        @SuppressWarnings("unchecked")
        List<RepositoryItem> facets = (List<RepositoryItem>) rule.getPropertyValue(FacetRuleProperty.FACETS);

        for (RepositoryItem facet : facets) {
            doc.addField("facetField", facet.getPropertyValue(FacetProperty.FIELD));
        }
    }

    private void setRankingRuleFields(RepositoryItem rule, SolrInputDocument doc) {
        String rankAction = null;

        if (BOOST_BY_FACTOR.equals(rule.getPropertyValue(BOOST_BY))) {
            String strength = (String) rule.getPropertyValue(STRENGTH);
            rankAction = mapStrength(strength);
        } else {
            rankAction = (String) rule.getPropertyValue(ATTRIBUTE);
            if (rankAction == null) {
                rankAction = "1.0";
            }
        }

        // TODO: add support for locales
        String conditionQuery = rulesBuilder.buildRankingRuleFilter(rule, Locale.US);
        StringBuilder boostFunctionQuery = new StringBuilder();

        if (conditionQuery.length() > 0) {
            boostFunctionQuery
                .append("if(exists(query({!lucene v='")
                .append(StringUtils.replace(conditionQuery, "'", "\\'"))
                .append("'})),")
                .append(rankAction)
                .append(",1.0)");
        } else {
            boostFunctionQuery.append(rankAction);
        }

        doc.setField(FIELD_BOOST_FUNCTION, boostFunctionQuery.toString());
    }

    /**
     * Maps the given strength to a boost factor
     *
     * @param strength is the strength name. See RankingRuleProperty
     * @return
     */
    protected String mapStrength(String strength) {
        if (strengthMap == null) {
            initializeStrengthMap();
        }

        String boostFactor = strengthMap.get(strength);

        if (boostFactor == null) {
            boostFactor = "1.0";
        }
        return boostFactor;
    }

    /**
     * Initializes the strength map.
     *
     * @TODO move this mappings to configuration file
     */
    private void initializeStrengthMap() {
        strengthMap = new HashMap<String, String>(STRENGTH_LEVELS);

        strengthMap.put(STRENGTH_MAXIMUM_DEMOTE, Float.toString(1/10f));
        strengthMap.put(STRENGTH_STRONG_DEMOTE, Float.toString(1/5f));
        strengthMap.put(STRENGTH_MEDIUM_DEMOTE, Float.toString(1/2f));
        strengthMap.put(STRENGTH_WEAK_DEMOTE, Float.toString(1/1.5f));
        strengthMap.put(STRENGTH_NEUTRAL, Float.toString(1f));
        strengthMap.put(STRENGTH_WEAK_BOOST, Float.toString(1.5f));
        strengthMap.put(STRENGTH_MEDIUM_BOOST, Float.toString(2f));
        strengthMap.put(STRENGTH_STRONG_BOOST, Float.toString(5f));
        strengthMap.put(STRENGTH_MAXIMUM_BOOST, Float.toString(10f));
    }

    /**
     * Helper method to set the category search token for document
     * 
     * @param doc
     *            the document to be indexed
     * @param category
     *            the category's repository item
     * @throws RepositoryException
     *             if an exception occurs while retrieving category info
     */
    private void setCategorySearchTokens(SolrInputDocument doc, RepositoryItem category, boolean includeSubcategories) throws RepositoryException {
        if (!"category".equals(category.getItemDescriptor().getItemDescriptorName())) {
            return;
        }
        @SuppressWarnings("unchecked")
        Set<String> searchTokens = (Set<String>) category.getPropertyValue(CategoryProperty.SEARCH_TOKENS);
        if (searchTokens != null) {
            for (String searchToken : searchTokens) {
                doc.addField(FIELD_CATEGORY, searchToken);
            }
        }

        if (includeSubcategories) {
            @SuppressWarnings("unchecked")
            List<RepositoryItem> childCategories = (List<RepositoryItem>) category
                    .getPropertyValue(CategoryProperty.CHILD_CATEGORIES);

            if (childCategories != null) {
                for (RepositoryItem childCategory : childCategories) {
                    setCategorySearchTokens(doc, childCategory, includeSubcategories);
                }
            }
        }
    }
}

