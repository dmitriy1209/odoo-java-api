/*
 *   Copyright 2011, 2014 De Bortoli Wines Pty Limited (Australia)
 *
 *   This file is part of OpenERPJavaAPI.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License. 
 *
 */
package com.odoojava.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Helps to simplify specifying filters for search and read operations.
 *
 * For example: FilterCollection filters = new FilterCollection();
 * filters.add("customer", "=", true); filters.add("active","=",true);
 *
 * To use a logical operator OR (type = many2one or type = many2many):
 * FilterCollection filters = new FilterCollection();
 * filters.add(FilterOperator.OR); filters.add("type", "=", "many2one");
 * filters.add("type", "=", "many2many");
 *
 * @author Pieter van der Merwe
 *
 */
public class FilterCollection {

    private final List<Object> filters = new ArrayList<>();

    /**
     * Filter operators like AND,OR,NOT. See the filter class help for more
     * info.
     *
     * @author Pieter van der Merwe
     *
     */
    public enum FilterOperator {
        AND,
        OR,
        NOT
    }

    /**
     * Adds a filter specification to the existing list of filters
     *
     * @param index Index where the specified filter should be added
     * @param fieldName Name of the model that should be filtered on
     * @param comparison For example =, !=, >, >=, <, <=, like, ilike, in, not
     * in, child_of, parent_left, parent_right @param value value that will be
     * compared to 'fieldName' usi
     * ng the 'comparison'
     * @return
     * @throws OdooApiException
     */
    public FilterCollection add(int index, String fieldName, String comparison, Object value) throws OdooApiException {
        if (fieldName == null) 
            throw new OdooApiException("First filter parameter is mandatory.  Please read the OpenERP help.");
        
        if (comparison == null) 
            throw new OdooApiException("Second filter parameter is mandatory.  Please read the OpenERP help.");
        
        Object[] filter = new Object[]{fieldName, comparison, value};
        filters.add(index, filter);
        return this;
    }

    /**
     * Adds a filter specification to the existing list of filters
     *
     * @param fieldName Name of the model that should be filtered on
     * @param comparison For example =, !=, >, >=, <, <=, like, ilike, in, not
     * in, child_of, parent_left, parent_right @param value value that will be
     * compared to 'fieldName' usi
     * ng the 'comparison'
     * @return
     * @throws OdooApiException
     */
    public FilterCollection add(String fieldName, String comparison, Object value) throws OdooApiException {
        add(filters.size(), fieldName, comparison, value);
        return this;
    }

    /**
     * Adds logical operators for filters
     *
     * From the OpenERP code: Domain criteria can be combined using 3 logical
     * operators than can be added between tuples: '**&**' (logical AND,
     * default), '**|**' (logical OR), '**!**' (logical NOT). These are
     * **prefix** operators and the arity of the '**&**' and '**|**' operator is
     * 2, while the arity of the '**!**' is just 1. Be very careful about this
     * when you combine them the first time.
     *
     * Here is an example of searching for Partners named *ABC* from Belgium and
     * Germany whose language is not english ::
     *
     * [('name','=','ABC'),'!',('language.code','=','en_US'),'|',('country_id.code','=','be'),('country_id.code','=','de'))
     *
     * The '&' is omitted as it is the default, and of course we could have used
     * '!=' for the language, but what this domain really represents is::
     *
     * (name is 'ABC' AND (language is NOT english) AND (country is Belgium OR
     * Germany))
     *
     * @param operator
     * @return
     */
    public FilterCollection add(FilterOperator operator) {
        add(filters.size(), operator);
        return this;
    }
    
    public FilterCollection add(Filter filter) throws OdooApiException {
        this.add(filter.getKey(), filter.getOperation(), filter.getValue());
        return this;
    }
    
    public FilterCollection add(Filter ... filters) throws OdooApiException {
        for (Filter filter : filters) this.add(filter);
        return this;
    }
    
    @lombok.Value
    public static class Filter {
        
        private String key;
        private String operation;
        private Object value;
        
        public Filter(String key, String operation, Object value) {
            this.key = key;
            this.operation = operation;
            this.value = value;
        }
        
        public Filter(String key, Object value) {
            this(key, "=", value);
        }
        
    }
    
    /**
     * Adds logical operators for filters
     *
     * From the OpenERP code: Domain criteria can be combined using 3 logical
     * operators than can be added between tuples: '**&**' (logical AND,
     * default), '**|**' (logical OR), '**!**' (logical NOT). These are
     * **prefix** operators and the arity of the '**&**' and '**|**' operator is
     * 2, while the arity of the '**!**' is just 1. Be very careful about this
     * when you combine them the first time.
     *
     * Here is an example of searching for Partners named *ABC* from Belgium and
     * Germany whose language is not english ::
     *
     * [('name','=','ABC'),'!',('language.code','=','en_US'),'|',('country_id.code','=','be'),('country_id.code','=','de'))
     *
     * The '&' is omitted as it is the default, and of course we could have used
     * '!=' for the language, but what this domain really represents is::
     *
     * (name is 'ABC' AND (language is NOT english) AND (country is Belgium OR
     * Germany))
     *
     * @param index Index where the specified filter should be added
     * @param operator
     * @return
     */
    public FilterCollection add(int index, FilterOperator operator) {
        switch (operator) {
            case OR:
                filters.add(index, "|");
                break;
            case NOT:
                filters.add(index, "!");
                break;
            default:
                break;
        }
        return this;
    }

    /**
     * Clears the filter from previous filter values
     */
    public void clear() {
        filters.clear();
    }

    /**
     * Gets the filters in a Object[] required by the XMLRPC calls to OpenERP
     *
     * @return
     */
    public Object[] getFilters() {
        return filters.toArray(new Object[filters.size()]);
    }

    /**
     * Returns the number of filters that are configured
     *
     * @return
     */
    public int size() {
        return filters.size();
    }
}
