package org.opencommercesearch;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.opencommercesearch.Facet.Filter;

import com.google.common.collect.Lists;

public class CategoryGraphBuilder {

    private static final String CATEGORY_PATH = "categoryPath";
    
    private CategoryGraph parentNode = new CategoryGraph();    
    
    public CategoryGraph getParentNode() {
        return parentNode;
    }

    public void setParentVO(CategoryGraph parentNode) {
        this.parentNode = parentNode;
    }

    public CategoryGraphBuilder() {
        List<CategoryGraph> childList = Lists.newArrayList();
        parentNode.setCategoryGraphNodes(childList);
    }
    
    public List<CategoryGraph> getCategoryGraphList() {
        return parentNode.getCategoryGraphNodes();
    }
    
    public void addPath(Filter filter){
        String filterPath = Utils.findFilterExpressionByName(filter.getPath(), CATEGORY_PATH);
        if(filterPath != null) {
            String[] pathArray = filterPath.split("\\"+SearchConstants.CATEGORY_SEPARATOR);
            parentNode.setId(pathArray[0]);
            recursiveAdd(filter, Arrays.copyOfRange(pathArray, 1 ,pathArray.length), parentNode);
        }  
    }

    private void recursiveAdd(Filter filter, String[] pathArray, CategoryGraph parentNode) {
        
        if(pathArray == null || pathArray.length == 0){
            return;
        }
        
        if(pathArray.length == 1){
            createNewNode(filter, parentNode);
        } else {
            
            String currentId = pathArray[0];
            CategoryGraph node = search(currentId, parentNode);
            
            if (node == null) {
                node = new CategoryGraph();
                List<CategoryGraph> childList = Lists.newArrayList();
                node.setCategoryGraphNodes(childList);
                node.setId(currentId);
                parentNode.getCategoryGraphNodes().add(node);
            }
            
            recursiveAdd(filter, Arrays.copyOfRange(pathArray, 1, pathArray.length), node);
        }

    }
    
    private void createNewNode(Filter filter, CategoryGraph parentNode) {
        
        CategoryGraph node = search(filter.getName(), parentNode);
        List<CategoryGraph> parentChildList = parentNode.getCategoryGraphNodes();
        
        if (node == null) {                
            node = new CategoryGraph();
            List<CategoryGraph> childList = Lists.newArrayList();
            node.setCategoryGraphNodes(childList);
            parentChildList.add(node);
        }
        
        node.setCount((int) filter.getCount());
        node.setPath(filter.getPath());
        node.setId(filter.getName());
        Collections.sort(parentChildList);
    }
    
    public CategoryGraph search(String id, CategoryGraph graphNode){
        
        if (id.equals(graphNode.getId())) {
            return graphNode;
        }
        
        CategoryGraph result = null;
        for (CategoryGraph childFacet : graphNode.getCategoryGraphNodes()) {
            result = search(id, childFacet);
            if (result != null) {
                break;
            }
        }
        return result;
    }
    
}