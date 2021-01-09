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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * *
 * Provides OpenERP field properties like data types, selection fields etc.
 *
 * @author Pieter van der Merwe
 *
 */

@lombok.ToString
public class Field implements Serializable{

    private static final long serialVersionUID = 4366619773447742242L;

    /**
     * OpenERP field types.
     *
     * @author Pieter van der Merwe
     *
     */
    public enum FieldType {
        
        INTEGER, CHAR, TEXT, BINARY, BOOLEAN, FLOAT, DATETIME, DATE, MANY2ONE, ONE2MANY, MANY2MANY, SELECTION;

        public static FieldType valueOfIgnoreCase(final String name, final FieldType defaultValue) {
            return Arrays.stream(FieldType.values())
                    .filter(fieldType -> fieldType.name().equalsIgnoreCase(name))
                    .findAny().orElse(defaultValue);
        }
        
    }

    @lombok.Getter    
    private final String name;
    private final Map<String, Object> openERPFieldData;

    public Field(String fieldName, Map<String, Object> openERPFieldData) {
        this.openERPFieldData = openERPFieldData;
        this.name = fieldName;
    }

    /**
     * *
     * Any property not covered by a get function can be fetched using this
     * function
     *
     * @param propertyName Name of property to fetch, for example 'name'.
     * @return The value associated with the property if any.
     */
    public Object getFieldProperty(String propertyName) {
        return (openERPFieldData.containsKey(propertyName)) ? openERPFieldData.get(propertyName) : null;
    }
    
    /**
     * Gets field property values for every object state
     *
     * @param propertyName
     * @return An array of values for all states in the format [state,
     * propvalue]
     */
    public Object[][] getStateProperties(String propertyName) {
        ArrayList<Object[]> stateValues = new ArrayList<>();
        @SuppressWarnings("unchecked")
        HashMap<String, Object> states = (HashMap<String, Object>) getFieldProperty("states");
        if (states == null) return stateValues.toArray(new Object[stateValues.size()][]);
        
        Object[] stateValue = new Object[2];
        for (Object stateKey : states.keySet()) {
            stateValue[0] = stateKey.toString();
            for (Object stateProperty : (Object[]) states.get(stateKey)) {
                Object[] statePropertyArr = (Object[]) stateProperty;
                if (statePropertyArr[0].toString().equals(propertyName)) {
                    stateValue[1] = statePropertyArr[1];
                    stateValues.add(stateValue);
                }
            }
        }
        return stateValues.toArray(new Object[stateValues.size()][]);
    }

    /**
     * *
     * Get the field description or label
     *
     * @return
     */
    public String getDescription() {
        return (String) getFieldProperty("string");
    }

    /**
     * Get the datatype of the field. If you want the original OpenERP type, use
     * getFieldProperty("type")
     *
     * @return
     */
    public FieldType getType() {
        return FieldType.valueOfIgnoreCase((String)getFieldProperty("type"), FieldType.CHAR);
    }    
//    public FieldType getType() {
//        final String fieldType = ((String) getFieldProperty("type")).toLowerCase();
//        switch (fieldType) {
//            case "char": return FieldType.CHAR;
//            case "text": return FieldType.TEXT;
//            case "integer": return FieldType.INTEGER;
//            case "binary": return FieldType.BINARY;
//            case "boolean": return FieldType.BOOLEAN;
//            case "float": return FieldType.FLOAT;
//            case "monetary": return FieldType.FLOAT;
//            case "datetime": return FieldType.DATETIME;
//            case "date": return FieldType.DATE;
//            case "many2one": return FieldType.MANY2ONE;
//            case "one2many": return FieldType.ONE2MANY;
//            case "many2many": return FieldType.MANY2MANY;
//            case "selection": return FieldType.SELECTION;
//            default: return FieldType.CHAR;
//    }
    
    /**
     * If a field is a selection field, the list of selecton options are
     * returned.
     *
     * @return
     */
    public ArrayList<SelectionOption> getSelectionOptions() {
        if (this.getType() != FieldType.SELECTION) return null;

        ArrayList<SelectionOption> options = new ArrayList<>();
        Object values = getFieldProperty("selection");
        if (values instanceof Object[]) {
            for (Object val : (Object[]) values) {
                Object[] multiVal = (Object[]) val;
                options.add(new SelectionOption(multiVal[0].toString(), multiVal[1].toString()));
            }
        }
        return options;
    }
    
    /**
     * Get the required property
     *
     * @return
     */
    public boolean getRequired() {
        return extractBoolean (getFieldProperty("required"), false);
    }

    /**
     * Get the selectable property
     *
     * @return
     */
    public boolean getSelectable() {
        return extractBoolean (getFieldProperty("selectable"), true);
    }
    
    /**
     * Get the store property
     *
     * @return
     */
    public boolean getStore() {
        return extractBoolean (getFieldProperty("store"), true);
    }

    /**
     * Get the func_method property
     *
     * @return
     */
    public boolean getFunc_method() {
        return extractBoolean (getFieldProperty("func_method"), false);
    }
    
    private boolean extractBoolean(final Object value, final boolean defaultValue) {
        return value == null ? defaultValue : (Boolean) value;
    }
    
    /**
     * Get the size property
     *
     * @return
     */
    public int getSize() {
        final Object value = getFieldProperty("size");
        return value == null ? 64 : (Integer) value;
    }

    /**
     * Get the help property
     *
     * @return
     */
    public String getHelp() {
        return (String) getFieldProperty("help");
    }
    
    /**
     * Get the relation property
     *
     * @return
     */
    public String getRelation() {
        final Object value = getFieldProperty("relation");
        return value == null ? "" : (String) value;
    }

    /**
     * Get the readonly property
     *
     * @return
     */
    public boolean getReadonly() {
        final Object value = getFieldProperty("readonly");
        if (value == null) return false;
        else return (Boolean) (value instanceof Integer ? (Integer) value == 1 : value);
    }
    
}
