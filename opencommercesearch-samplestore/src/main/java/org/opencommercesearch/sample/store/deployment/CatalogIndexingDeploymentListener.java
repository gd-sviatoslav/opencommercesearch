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
package org.opencommercesearch.sample.store.deployment;

import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.opencommercesearch.deployment.EvaluationServiceSender;
import org.opencommercesearch.feed.SearchFeed;
import org.opencommercesearch.sample.store.ATGLoggingUtil;
import atg.deployment.common.Status;
import atg.deployment.common.event.DeploymentEvent;
import atg.deployment.common.event.DeploymentEventListener;
import atg.nucleus.GenericService;

/**
 * Component of this class should be added to the DeploymentAgent configuration for all target sites. 
 * This class receives deployments events and will trigger products indexing.
 * This component can be configured to trigger in a specific deployment state. Unless required, leave the default value (deployment complete).
 * 
 * @author S.L. (slisenkin at griddynamics dot com) 13.06.2013 14:29:54
 */
public class CatalogIndexingDeploymentListener extends GenericService implements DeploymentEventListener {

    private String triggerStatus;
    private String productRepositoryName;
    
    private boolean enableEvaluation;
    private EvaluationServiceSender evaluationServiceSender;
    
    private SearchFeed searchFeed;
    
    public CatalogIndexingDeploymentListener() {
    }
    
    public SearchFeed getSearchFeed() {
        return searchFeed;
    }
    
    public void setSearchFeed(SearchFeed searchFeed) {
        this.searchFeed = searchFeed;
    }
    
    public String getTriggerStatus() {
        return triggerStatus;
    }

    public void setTriggerStatus(String triggerStatus) {
        this.triggerStatus = triggerStatus;
    }

    public String getProductRepositoryName() {
        return productRepositoryName;
    }
    
    public void setProductRepositoryName(String productRepositoryName) {
        this.productRepositoryName = productRepositoryName;
    }
    
    public EvaluationServiceSender getEvaluationServiceSender() {
		return evaluationServiceSender;
	}

	public void setEvaluationServiceSender(EvaluationServiceSender evaluationServiceSender) {
		this.evaluationServiceSender = evaluationServiceSender;
	}
  
    public boolean isEnableEvaluation() {
        return enableEvaluation;
    }

    public void setEnableEvaluation(boolean enableEvaluation) {
        this.enableEvaluation = enableEvaluation;
    }
	
    private boolean isSet(){
        return StringUtils.isNotBlank(getProductRepositoryName()) // --
                && (null != getSearchFeed()) // --
                && StringUtils.isNotBlank(getTriggerStatus());
    }
    
	@Override
    public void deploymentEvent(DeploymentEvent event) {
	    ATGLoggingUtil.info(this, "Deployment event received: {0}", Status.stateToString(event.getNewState()));
	    if(!isSet()){
	        ATGLoggingUtil.error(this, "Component is not properly configured! Will not process events.");
	        return;
	    }
	    
        if (Status.stateToString(event.getNewState()).equals(getTriggerStatus())) {
            
            if(isProductRepositoryUpdated(event)){
                ATGLoggingUtil.info(this, "Triggering catalog index update..");
                
                reportIndexingState("indexing_started");
                try {
                    getSearchFeed().startFullFeed();
                    reportIndexingState("indexing_finished");    
                } catch (Exception e) {
                    ATGLoggingUtil.error(this, e, "Indexing failed with error.");
                    reportIndexingState("indexing_failed");
                }
                
            } else {
                ATGLoggingUtil.info(this, "No catalog index update triggered yet.");
            }
        }
    }
	
	private void reportIndexingState(String stateMsg){
	    if(isEnableEvaluation() && null != getEvaluationServiceSender()) {
            getEvaluationServiceSender().sendMessage(stateMsg + ":" + ATGLoggingUtil.FORMAT_DATE.format(new Date())); 
        }
	}
	
	private boolean isProductRepositoryUpdated(DeploymentEvent event){	    
	    @SuppressWarnings("unchecked")
        Map<String, Set<String>> affectedItemTypes = event.getAffectedItemTypes();
        ATGLoggingUtil.debug(this, "Deployment event received: {0} -> {1}", getTriggerStatus(), affectedItemTypes);
        
        if (affectedItemTypes != null) {
            for (Entry<String, Set<String>> entry : affectedItemTypes.entrySet()) {
                String repositoryName = entry.getKey();
                if(getProductRepositoryName().equals(repositoryName)){
                    return true;
                }
            }
        }
        
        return false;
	}

}
