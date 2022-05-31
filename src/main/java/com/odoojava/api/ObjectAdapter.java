/*
 *   Copyright 2011, 2014 De Bortoli Wines Pty Limited (Australia)
 *
 *   This file is part of OdooJavaAPI.
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.xmlrpc.XmlRpcException;
import com.odoojava.api.Field.FieldType;
import com.odoojava.api.helpers.FilterHelper;
import java.util.Locale;
import java.util.function.Function;

/**
 * Main class for communicating with the server. It provides extra validation for making calls to the Odoo server. It
 * converts data types, validates model names, validates filters, checks for nulls etc.
 *
 * @author Pieter van der Merwe
 *
 */
public class ObjectAdapter {

    private final String modelName;
    private final OdooCommand command;
    private final FieldCollection allFields;
    private final Version serverVersion;

    // Object name cache so the adapter doesn't have to reread model names from
    // the database for every new object.
    // Bulk loads/reads can become very slow if every adapter requires a call
    // back to the server
    private static final List<String> objectNameCache = new ArrayList<>();

    // Object workflow signal cache so the adapter doesn't have to reread signal
    // names from the database for every workflow call.
    private static final List<String> signalCache = new ArrayList<>();

    // Cache used to store the name_get result of an model to cater for
    // many2many relations in the import function
    // It is cleared every time the import function is called for a specific
    // object
    private final Map<String, Map<String, String>> modelNameCache = new ConcurrentHashMap<>();

    /**
     * Default constructor
     *
     * @param session Session object that will be used to make the calls
     * @param modelName Model name that this adapter will work for.
     * @throws OdooApiException
     */
    public ObjectAdapter(Session session, String modelName) throws OdooApiException {
        this(new OdooCommand(session), modelName, session.getServerVersion());
    }

    ObjectAdapter(OdooCommand command, String modelName, Version serverVersion) throws OdooApiException {
        this.command = command;
        this.modelName = modelName;
        this.serverVersion = serverVersion;

        validateModelExists();

        allFields = getFields();
    }

    /**
     * Validates a model name against entries in ir.model The function is synchronized to make use of a global (static)
     * list of model names to increase speed
     *
     * @param command Command object to use
     * @throws OdooApiException If the model could not be validated
     */
    @SuppressWarnings("unchecked")
    private synchronized void validateModelExists() throws OdooApiException {
        try {
            // If you can't find the object name, reload the cache. Somebody may
            // have added a new module after the cache was created
            // Ticket #1 from sourceforge

            Object[] ids = null;
            if (objectNameCache.indexOf(modelName) < 0) {
                clearModelNameCache();
                //TODO: Improve this part by using appropriate filterhelper
                Response response = command.searchObject("ir.model", new Object[]{});
                if (response.isSuccessful()) {
                    ids = response.getResponseObjectAsArray();
                    Object[] result = command.readObject("ir.model", ids, new String[]{"model"});
                    for (Object row : result) {
                        objectNameCache.add(((HashMap<String, Object>) row).get("model").toString());
                    }
                }

            }

            if (objectNameCache.indexOf(modelName) < 0) {
                throw new OdooApiException("Could not find model with name '" + modelName + "'");
            }
        } catch (XmlRpcException ex) {
            throw new XmlRpcRuntimeException(ex);
        }
    }

    static void clearModelNameCache() {
        objectNameCache.clear();
    }

    private void checkSignalExists(String signal) throws OdooApiException {
        // If you can't find the signal, reload the cache. Somebody may have
        // added a new module after the cache was created
        String signalCombo = modelName + "#" + signal;
        if (!signalCache.contains(signalCombo)) {
            // Only one thread need to do the updating
            synchronized (this.getClass()) {
                // Cache may now contain the signal (updated by another thread
                // while waiting at the synchronized gate).
                if (!signalCache.contains(signalCombo)) {
                    refreshSignalCache(command);
                }
            }
        }

        // If still not found, this is an error...
        if (!signalCache.contains(signalCombo)) {
            throw new OdooApiException(
                    "Could not find signal with name '" + signal + "' for object '" + modelName + "'");
        }
    }

    @SuppressWarnings("unchecked")
    private static void refreshSignalCache(OdooCommand command) throws OdooApiException {
        signalCache.clear();
        try {
            Response response = command.searchObject("workflow.transition", new Object[]{});
            //Object[] ids = new Object[]{};
            if (response.isSuccessful()) {
                Object[] ids = response.getResponseObjectAsArray();

                Object[] result = command.readObject("workflow.transition", ids, new String[]{"signal", "wkf_id"});
                for (Object row : result) {
                    /*
				 * Get the parent workflow to work out get the object name
                     */
                    //TODO: Check the latest syntax or workflow
                    int wkfId = Integer.parseInt(((Object[]) ((HashMap<String, Object>) row).get("wkf_id"))[0].toString());
                    Object[] workflow = command.readObject("workflow", new Object[]{wkfId}, new String[]{"osv"});

                    String obj = ((HashMap<String, Object>) workflow[0]).get("osv").toString();
                    String sig = ((HashMap<String, Object>) row).get("signal").toString();
                    signalCache.add(obj + "#" + sig);
                }
            }
        } catch (XmlRpcException e) {
            throw new OdooApiException("Could not validate signal name: ", e);
        }
    }

//    /**
//     * Prepares a ROW object to be used for setting values in import/write methods
//     *
//     * @param fields Fields that should be included in the row definition
//     * @return An empty row with the specified fields
//     * @throws OdooApiException
//     */
//    public Row getNewRow(FieldCollection fields) throws OdooApiException {
//        return new Row(new HashMap<>(), fields);
//    }
//
//    /**
//     * Prepares a ROW object to be used for setting values in import/write methods. This method calls to the server to
//     * get the fieldCollection. Use getNewRow(FieldCollection fields) if you can to reduce the number of calls to the
//     * server for a bulk load.
//     *
//     * @param fields Fields that should be included in the row definition
//     * @return An empty row with the specified fields
//     * @throws OdooApiException
//     */
//    public Row getNewRow(String[] fields) throws OdooApiException {
//        return getNewRow(getFields(fields));
//    }

    /**
     * Reads objects from the Odoo server if you already have the ID's. If you don't, use searchAndRead with filters.
     *
     * @param ids List of ids to fetch objects for
     * @param fields List of fields to fetch data for
     * @return A collection of rows for an Odoo object
     * @throws OdooApiException
     */
    private FieldCollection createFieldCollection(String[] fields) {
        // Faster to do read existing fields that to do a server call again
        FieldCollection fieldCol = new FieldCollection();
        for (String fieldName : fields) {
            for (Field fld : allFields) {
                if (fld.getName().equals(fieldName)) {
                    fieldCol.add(fld);
                }
            }
        }
        return fieldCol;
    }

    public RowCollection readObject(Object[] ids, String[] fields) throws OdooApiException {

        try {
            // Faster to do read existing fields that to do a server call again
            Object[] results = command.readObject(modelName, ids, fields);
            /**
             * **
             * 18/04/2012 - PvdM Maybe reconsider this piece of code for later. Does it matter if it isn't sorted by ID?
             *
             * // Odoo doesn't use the sorting you pass (specified in the search function to get a sorted list of IDs).
             * // When they fix it, remove this section of code ArrayList<Integer> idList = new ArrayList
             * <Integer>(); for (Object id : ids){ idList.add(Integer.parseInt(id.toString())); } Object[] sortedResults
             * = new Object[ids.length]; for (Object result : results){
             *
             * @SuppressWarnings("unchecked") int id = null null null null null null null null             Integer.parseInt(((HashMap<String,
             * Object>)result).get("id").toString()); sortedResults[idList.indexOf(id)] = result; } **
             */
            return new RowCollection(results, createFieldCollection(fields));
        } catch (XmlRpcException ex) {
            throw new XmlRpcRuntimeException(ex);
        }
    }

    /**
     * *
     * Fetches field information for the current Odoo object this adapter is linked to
     *
     * @return FieldCollecton data for all fields of the object
     */
    private FieldCollection getFields() {
        return getFields(new String[]{});
    }

    /**
     * *
     * Fetches field names for the current Odoo object this adapter is linked to
     *
     * @return Array of field names
     */
    public String[] getFieldNames() {
        FieldCollection fields = getFields(new String[]{});
        String[] fieldNames = new String[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            fieldNames[i] = fields.get(i).getName();
        }
        return fieldNames;
    }

    /**
     * *
     * Fetches field information for the current Odoo object this adapter is linked to
     *
     * @param filterFields Only return data for files in the filter list
     * @return FieldCollecton data for selected fields of the object
     */
    @SuppressWarnings("unchecked")
    public FieldCollection getFields(String[] filterFields) {
        try {
            return command.getFields(modelName, filterFields).entrySet().stream()
                    .map(e -> new Field(e.getKey(), (Map<String, Object>) e.getValue()))
                    .collect(Collectors.toCollection(FieldCollection::new));
        } catch (XmlRpcException ex) {
            throw new XmlRpcRuntimeException(ex);
        }
    }

    /**
     * Helper function to validate filter parameters and returns a filter object suitable for the Odoo search function
     * by fixing data types and converting values where appropriate.
     *
     * @param filters FilterCollection containing the specified filters
     * @return A validated filter Object[] correctly formatted for use by the Odoo search function
     * @throws OdooApiException
     */
    public Object[] validateFilters(final FilterCollection filters) throws OdooApiException {

        if (filters == null) {
            return new Object[0];
        }

        ArrayList<Object> processedFilters = new ArrayList<>();

        for (int i = 0; i < filters.getFilters().length; i++) {
            Object filter = filters.getFilters()[i];

            if (filter == null) throw new IllegalArgumentException("null filter parameter is not allowed");

            // Is a logical operator
            if (filter instanceof String) {
                processedFilters.add(filter);
                continue;
            }

            // Is a comparison filter
            Object[] filterObjects = (Object[]) filter;
            if (!(filter instanceof Object[]) || filterObjects.length != 3) {
                throw new OdooApiException("Filters aren't in the correct format.  Please read the Odoo help.");
            }

            String fieldName = filterObjects[0].toString();
            String comparison = filterObjects[1].toString();
            Object value = filterObjects[2];

            Field fld = findFieldByName(fieldName);

            // Can't search on calculated fields
            if (fld != null && fld.getFunc_method()) {
                throw new OdooApiException("Can not search on function field " + fieldName);
            }

            // Fix the value type if required for the Odoo server
            if (!"id".equals(fieldName) && fld == null) {
                throw new OdooApiException("Unknow filter field " + fieldName);
            } else if ("is null".equals(comparison)) {
                comparison = "=";
                value = false;
            } else if ("is not null".equals(comparison)) {
                comparison = "!=";
                value = false;
            } else if (fld != null && fld.getType() == FieldType.BOOLEAN && !(value instanceof Boolean)) {
                value = convertToBoolean(value);
            } else if (fld != null && fld.getType() == FieldType.FLOAT && !(value instanceof Double)) {
                value = Double.parseDouble(value.toString());
            } else if (fld != null && fld.getType() == FieldType.DATE && value instanceof Date) {
                value = new SimpleDateFormat("yyyy-MM-dd").format((Date) value);
            } else if (fld != null && fld.getType() == FieldType.DATETIME && value instanceof Date) {
                value = new SimpleDateFormat("yyyy-MM-dd HH:mm").format((Date) value);
            } else if ("=".equals(comparison)) {

                // If a integer field is not an integer in a '=' comparison,
                // parse it as an int
                if (!(value instanceof Integer)) {
                    if ("id".equals(fieldName)
                            || (fld != null && fld.getType() == FieldType.INTEGER && !(value instanceof Integer))
                            || (fld != null && fld.getType() == FieldType.MANY2ONE && !(value instanceof Integer))) {
                        value = Integer.parseInt(value.toString());
                    }
                }
            } else if ("in".equalsIgnoreCase(comparison)) {
                if (value instanceof String) {
                    // Split by , where the , isn't preceded by a \
                    String[] entries = value.toString().split("(?<!\\\\),");
                    Object[] valueArr = new Object[entries.length];
                    for (int entrIdx = 0; entrIdx < entries.length; entrIdx++) {
                        String entry = FilterHelper.csvDecodeString(entries[entrIdx]);

                        // For relation fields or integer fields we build an
                        // array of integers
                        if (fld != null
                                && (fld.getType() == FieldType.INTEGER || fld.getType() == FieldType.ONE2MANY
                                || fld.getType() == FieldType.MANY2MANY || fld.getType() == FieldType.MANY2ONE)
                                || "id".equals(fieldName)) {
                            valueArr[entrIdx] = Integer.parseInt(entry);
                        } else {
                            valueArr[entrIdx] = entry;
                        }
                    }
                    value = valueArr;
                } // If it is a single value, just put it in an array
                else if (!(value instanceof Object[])) {
                    value = new Object[]{value};
                }
            }

            processedFilters.add(new Object[]{fieldName, comparison, value});

        }

        return processedFilters.toArray(new Object[processedFilters.size()]);
    }

    private Object convertToBoolean(Object value) throws OdooApiException {
        if (value instanceof String) {
            char firstchar = value.toString().toLowerCase(Locale.getDefault()).charAt(0);
            switch (firstchar) {
                case '1':
                case 'y':
                case 't':
                    return true;
                case '0':
                case 'n':
                case 'f':
                    return false;
                default:
                    throw new OdooApiException("Unknown boolean " + value.toString());
            }
        }
        return value;
    }

    private Field findFieldByName(String fieldName) {
        for (Field field : allFields) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    private String[] getFieldListForImport(FieldCollection currentFields) {
        return Stream.concat(
                Stream.of(".id"), currentFields.stream()
                // Return field name, adding ".id" if type is MANY2ONE
                .map(field -> field.getType() == FieldType.MANY2ONE ? field.getName() + ".id" : field.getName()))
                //.map(ObjectAdapter::getFieldNameForImport))
                .toArray(String[]::new);
    }

//    private static String getFieldNameForImport(Field field) {
//        // Return field name, adding ".id" if type is MANY2ONE
//        return field.getType() == FieldType.MANY2ONE ? field.getName() + ".id" : field.getName();
//    }
    /**
     * Calls the import_data or load function on the server to bulk create/update records.
     *
     * The import_data function will be called on Odoo servers where the version number is < 7. The import_data function
     * does not return IDs and therefore IDs will not be set on imported rows.
     *
     * The load function will be called for V7 and the IDs will be set on the imported rows. The load function was
     * introduced in V7 and the import_data function deprecated.
     *
     * @param rows Rows to import.
     * @return If the import was successful
     * @throws OdooApiException
     */
    public boolean importData(final RowCollection rows) throws OdooApiException {
        try {
            if (this.serverVersion.getMajor() >= 7) {
                final RowCollection newRows = new RowCollection();
                final RowCollection oldRows = new RowCollection();
                for (int i = 0; i < rows.size(); ++i) {
                    if (rows.get(i).getID() == 0) {
                        newRows.add(rows.get(i));
                    } else {
                        oldRows.add(rows.get(i));
                    }
                }
                if (!newRows.isEmpty() && !oldRows.isEmpty()) {
                    return this.importData(oldRows) && this.importData(newRows);
                }
            }
            this.modelNameCache.clear();
            final Object[][] importRows = new Object[rows.size()][];
            for (int j = 0; j < rows.size(); ++j) {
                final MapRow mapRow = rows.get(j);
                importRows[j] = this.fixImportData(mapRow);
            }
            if (this.serverVersion.getMajor() >= 7) {
                this.importDataV7(rows, importRows);
            } else {
                this.importDataLegacy(rows, importRows);
            }
            return true;
        } catch (XmlRpcException ex) {
            throw new XmlRpcRuntimeException((Throwable) ex);
        }
    }

    private Object[] fixImportData(final MapRow inputMapRow) throws OdooApiException {
        try {
            final Object[] outputRow = new Object[inputMapRow.getFields().size() + 1];
            outputRow[0] = inputMapRow.get("id");
            if (outputRow[0] == null) {
                outputRow[0] = 0;
            } else {
                outputRow[0] = Integer.parseInt(inputMapRow.get("id").toString());
            }
            for (int i = 0; i < inputMapRow.getFields().size(); ++i) {
                final int columnIndex = i + 1;
                final Field fld = inputMapRow.getFields().get(i);
                final String fieldName = fld.getName();
                Object value = inputMapRow.get(fieldName);
                outputRow[columnIndex] = value;
                if (fld.getType() == Field.FieldType.MANY2ONE) {
                    if (value == null) {
                        outputRow[columnIndex] = 0;
                    } else {
                        outputRow[columnIndex] = Integer.parseInt(value.toString());
                    }
                } else if (value == null) {
                    outputRow[columnIndex] = false;
                } else {
                    value = this.formatValueForWrite(fld, value);
                    switch (fld.getType()) {
                        case SELECTION: {
                            boolean validValue = false;
                            for (final SelectionOption option : fld.getSelectionOptions()) {
                                if (option.code.equals(value.toString())) {
                                    validValue = true;
                                    outputRow[columnIndex] = option.value;
                                    break;
                                }
                                if (option.value.equals(value.toString())) {
                                    outputRow[columnIndex] = value;
                                    validValue = true;
                                    break;
                                }
                            }
                            if (!validValue) {
                                throw new OdooApiException("Could not find a valid value for section field " + fieldName + " with value " + value);
                            }
                            break;
                        }
                        case MANY2MANY: {
                            Map<String, String> idToName;
                            if (!this.modelNameCache.containsKey(fld.getRelation())) {
                                idToName = new HashMap<String, String>();
                                final Response response = this.command.searchObject(fld.getRelation(), new Object[0]);
                                if (response.isSuccessful()) {
                                    final Object[] ids = response.getResponseObjectAsArray();
                                    final Object[] names = this.command.nameGet(fld.getRelation(), ids);
                                    for (int j = 0; j < ids.length; ++j) {
                                        final Object[] nameValue = (Object[]) names[j];
                                        idToName.put(nameValue[0].toString(), nameValue[1].toString());
                                    }
                                }
                                this.modelNameCache.put(fld.getRelation(), idToName);
                            } else {
                                idToName = this.modelNameCache.get(fld.getRelation());
                            }
                            String newValue = "";
                            if (value instanceof String) {
                                for (final String singleID : value.toString().split(",")) {
                                    if (!idToName.containsKey(singleID)) {
                                        throw new OdooApiException("Could not find " + fld.getRelation() + " with ID " + singleID);
                                    }
                                    newValue = newValue + "," + idToName.get(singleID);
                                }
                            } else {
                                for (final Object singleID2 : (Object[]) value) {
                                    if (!idToName.containsKey(singleID2.toString())) {
                                        throw new OdooApiException("Could not find " + fld.getRelation() + " with ID " + singleID2.toString());
                                    }
                                    newValue = newValue + "," + idToName.get(singleID2.toString());
                                }
                            }
                            outputRow[columnIndex] = newValue.substring(1);
                            break;
                        }
                        default: {
                            outputRow[columnIndex] = value.toString();
                            break;
                        }
                    }
                }
            }
            return outputRow;
        } catch (XmlRpcException ex) {
            throw new XmlRpcRuntimeException((Throwable) ex);
        }
    }

    private void importDataLegacy(RowCollection rows, Object[][] importRows) throws XmlRpcException, OdooApiException {

        String[] targetFieldList = getFieldListForImport(rows.get(0).getFields());

        Object[] result = command.importData(modelName, targetFieldList, importRows);

        // Should return the number of rows committed. If there was an
        // error, it returns -1
        if ((Integer) result[0] != importRows.length) {
            throw new OdooApiException(result[2].toString() + "\nRow :" + result[1].toString() + "");
        }
    }

    @SuppressWarnings("unchecked")
    private void importDataV7(final RowCollection rows, Object[][] importRows) throws XmlRpcException, OdooApiException {
        String[] targetFieldList = this.getFieldListForImport(rows.get(0).getFields());
        if (this.serverVersion.getMajor() >= 7 && !rows.isEmpty() && rows.get(0).getID() == 0) {
            final String[] newTargetFieldList = new String[targetFieldList.length - 1];
            for (int i = 1; i < targetFieldList.length; ++i) {
                newTargetFieldList[i - 1] = targetFieldList[i];
            }
            targetFieldList = newTargetFieldList;
            final Object[][] newImportRows = new Object[rows.size()][];
            for (int j = 0; j < importRows.length; ++j) {
                final Object[] newRow = new Object[importRows[j].length - 1];
                for (int k = 1; k < importRows[j].length; ++k) {
                    newRow[k - 1] = importRows[j][k];
                }
                newImportRows[j] = newRow;
            }
            importRows = newImportRows;
        }
        final Map<String, Object> results = this.command.load(this.modelName, targetFieldList, importRows);
        if (results.get("ids") instanceof Boolean) {
            final Map<String, Object>[] messages = (Map<String, Object>[]) results.get("messages");
            final String errorString = Arrays.stream(messages)
                    .flatMap(m -> m.entrySet().stream()).map(e -> String.join(":", e.getKey(), e.getValue().toString()))
                    .collect(Collectors.joining("\n"));
            throw new OdooApiException(errorString);
        }
        final Object[] ids = (Object[]) results.get("ids");
        for (int j = 0; j < rows.size(); ++j) {
            final MapRow mapRow = rows.get(j);
            mapRow.put("id", ids[j]);
        }
    }

    /**
     * Gets the number of records that satisfies the filter
     *
     * @param filter A filter collection that contains a list of filters to be applied
     * @return The number of record count.
     * @throws OdooApiException
     */
    public int getObjectCount(FilterCollection filter) throws OdooApiException {
        try {
            Integer count = 0;
            Object[] preparedFilters = validateFilters(filter);

            Response response = command.searchObject(modelName, preparedFilters, -1, -1, null, true);
            if (response.isSuccessful()) {
                count = Integer.parseInt(response.getResponseObject().toString());
            }
            return count;
        } catch (XmlRpcException ex) {
            throw new XmlRpcRuntimeException(ex);
        }
    }

    /**
     * *
     * Combines the searchObject and readObject calls. Allows for easy read of all data
     *
     * @param filter A filter collection that contains a list of filters to be applied
     * @param fields List of fields to return data for
     * @return A collection of rows for an Odoo object
     * @throws OdooApiException
     */
    public RowCollection searchAndReadObject(FilterCollection filter, String[] fields) throws OdooApiException {
        return searchAndReadObject(filter, fields, -1, -1, "");
    }

    /**
     * Combines the searchObject and readObject calls and returns rows in batches. Useful for multi-threaded ETL
     * applications.
     *
     * @param filter A filter collection that contains a list of filters to be applied
     * @param fields List of fields to return data for
     * @param offset Number of records to skip. -1 for no offset.
     * @param limit Maximum number of rows to return. -1 for no limit.
     * @param order Field name to order on
     * @return A collection of rows for an Odoo object
     * @throws OdooApiException
     */
    public RowCollection searchAndReadObject(final FilterCollection filter, final String[] fields, int offset,
            int limit, String order) throws OdooApiException {

        try {
            String[] fieldArray = fields == null ? new String[]{} : fields;
            Object[] preparedFilters = validateFilters(filter);
            Object[] idList = null;
            Response response = command.searchObject(modelName, preparedFilters, offset, limit, order, false);
            if (response.isSuccessful()) {
                idList = response.getResponseObjectAsArray();
            }
            if (this.serverVersion.getMajor() > 13) {
                Object[] results = command.executeCommandRead(modelName, fieldArray, idList);
                RowCollection rowCollection = new RowCollection(results, createFieldCollection(fieldArray));
                return rowCollection;
            }
            else return readObject(idList, fieldArray);
        } catch (XmlRpcException ex) {
            throw new XmlRpcRuntimeException(ex);
        }

    }

    private Object formatValueForWrite(Field fld, Object value) {
        return value == null ? false : formatValueBasedOnFieldTypeForWrite(fld, value);
    }

    private Object formatValueBasedOnFieldTypeForWrite(Field fld, Object value) {
        switch (fld.getType()) {
            case BOOLEAN:
                return value;
            case FLOAT:
                return Double.valueOf(value.toString());
            case MANY2MANY:
                return formatManyToManyForWrite(value);
            case MANY2ONE:
            case ONE2MANY: {
                return formatOneToManyForWrite(value);
            }
            case INTEGER:
                return formatIntegerForWrite(value);
            case DATE:
                return DateTimeFormatter.formatDateForWrite(value);
            case DATETIME:
                return DateTimeFormatter.formatDateTimeForWrite(value);
            default:
                return value.toString();
        }
    }

    private Object formatManyToManyForWrite(Object value) {
        // For write, otherwise it is a comma separated list of strings used
        // by import
        if (value instanceof Object[]) {
            Object[] tmp = new Object[]{6, 0, (Object[]) value};
            return new Object[][]{tmp};
        } else {
            return value;
        }
    }

    private Object formatOneToManyForWrite(final Object value) {
        if (value instanceof Object[]) {
            final Object[] tmp = {6, 0, (Object[]) value};
            return new Object[][]{tmp};
        }
        return value;
    }

    private Object formatIntegerForWrite(Object value) {
        try {
            // To make sure 1.0 is converted to 1
            return Double.valueOf(value.toString()).intValue();
        } catch (NumberFormatException e) {
            //for versions 8 and 9 id can be array with legth 2 (id and name). for example [5, Administrator]
            final Object[] arr = (Object[]) value;
            return Double.valueOf(arr[0].toString()).intValue();
        }
    }

    /**
     * Writes a collection of rows to the database by calling the write function on the object the Row is holding data
     * for
     *
     * @param rows Row collection to submit
     * @param changesOnly Only changed values will be submitted to the database.
     * @return An array of logicals. One for each row to indicate if the update was successful
     * @throws OdooApiException
     */
    public Boolean[] writeObject(final RowCollection rows, final boolean changesOnly) throws OdooApiException {
        Boolean[] returnValues = new Boolean[rows.size()];

        for (int i = 0; i < rows.size(); i++) {
            returnValues[i] = writeObject(rows.get(i), changesOnly);
        }

        return returnValues;
    }

    public boolean writeObject(final MapRow mapRow, final boolean changesOnly) throws OdooApiException {
        final Object idObj = mapRow.get("id");
        if (idObj == null || Integer.parseInt(idObj.toString()) <= 0) {
            throw new OdooApiException("Please set the id field with the database ID of the object");
        }
        final int id = Integer.parseInt(idObj.toString());
        final Map<String, Object> valueList = this.collectValues(mapRow, changesOnly);
        if (valueList.isEmpty()) {
            return false;
        }
        try {
            final boolean success = this.command.writeObject(this.modelName, id, valueList);
            if (success) {
                mapRow.changesApplied();
            }
            return success;
        } catch (XmlRpcException e) {
            throw new OdooApiException((Throwable) e);
        }
    }

    /**
     * Writes a Row to the database by calling the write function on the object the Row is holding data for
     *
     * @param row Row to be committed
     * @param changesOnly Only changed values will be submitted to the database.
     * @return If the update was successful
     * @throws OdooApiException
     */
//    public boolean writeObject(final Row row, boolean changesOnly) throws OdooApiException {
//
//        final Object idObj = row.get("id");
//        if (idObj == null || Integer.parseInt(idObj.toString()) <= 0) {
//            throw new OdooApiException("Please set the id field with the database ID of the object");
//        }
//        int id = Integer.parseInt(idObj.toString());
//        final Map<String, Object> valueList = collectValues(row, changesOnly);
//        if (valueList.isEmpty()) {
//            return false;
//        }
//
//        try {
//            boolean success = command.writeObject(modelName, id, valueList);
//            if (success) {
//                row.changesApplied();
//            }
//            return success;
//        } catch (XmlRpcException e) {
//            throw new OdooApiException(e);
//        }
//
//    }

//    private Map<String, Object> collectValues(final Row row, boolean changesOnly) {
//        return (changesOnly ? row.getChangedFields() : row.getFields()).stream()
//                .collect(Collectors.toMap(Field::getName, field -> formatValueForWrite(field, row.get(field))));
//    }

    private Map<String, Object> collectValues(final MapRow mapRow, final boolean changesOnly) {
        return (changesOnly ? mapRow.getChangedFields() : mapRow.getFields()).stream()
                .collect(Collectors.toMap((Function<? super Field, ? extends String>) Field::getName, field -> this.formatValueForWrite(field, mapRow.get(field))));
    }

    public void createObject(final MapRow mapRow) throws OdooApiException {
        try {
            final Map<String, Object> valueList = this.collectValues(mapRow, false);
            if (valueList.isEmpty()) {
                throw new OdooApiException("Row doesn't have any fields to update");
            }
            final Object id = this.command.createObject(this.modelName, valueList);
            mapRow.put("id", id);
            mapRow.changesApplied();
        } catch (XmlRpcException ex) {
            throw new XmlRpcRuntimeException((Throwable) ex);
        }
    }

//    /**
//     * Creates an Object on the Odoo server by calling the create function on the server. The id column is set on the
//     * row after the object was successfully created
//     *
//     * @param row Data row read data from to create the Object
//     * @throws OdooApiException
//     */
//    public void createObject(final Row row) throws OdooApiException {
//
//        try {
//            final Map<String, Object> valueList = collectValues(row, false);
//
//            if (valueList.isEmpty()) {
//                throw new OdooApiException("Row doesn't have any fields to update");
//            }
//
//            Object id = command.createObject(modelName, valueList);
//
//            row.put("id", id);
//            row.changesApplied();
//        } catch (XmlRpcException ex) {
//            throw new XmlRpcRuntimeException(ex);
//        }
//
//    }

    /**
     * Calls any function on an object that returns a field collection. ie. a row is retured as [{'name' : {'type' :
     * 'char'}] The Odoo function must have the signature like (self, cr, uid, *param).
     *
     * @param functionName function to call
     * @param parameters Additional parameters that will be passed to the object
     * @return A field collection
     * @throws OdooApiException
     */
    public FieldCollection callFieldsFunction(String functionName, Object[] parameters) throws OdooApiException {
        Response response = command.callObjectFunction(modelName, functionName, parameters);
        return callFieldsFunction(response);
    }

    @SuppressWarnings("unchecked")
    private FieldCollection callFieldsFunction(Response response) {
        if (!response.isSuccessful()) {
            return new FieldCollection();
        }

        FieldCollection fieldCol = new FieldCollection();

        Object[] results = response.getResponseObjectAsArray();
        // Go through the first row and fetch the fields name, description and
        // type
        if (results.length > 0) {
            Map<String, Object> rowMap = (Map<String, Object>) results[0];
            for (Entry<String, Object> entry : rowMap.entrySet()) {
                Map<String, Object> fldDetails;
                if (entry.getValue() instanceof Map<?, ?>) {
                    fldDetails = (Map<String, Object>) entry.getValue();
                } else {
                    fldDetails = new HashMap<>();
                }

                completeFieldDetailsIfNecessary(entry, fldDetails);
                fieldCol.add(new Field(entry.getKey(), fldDetails));
            }
        }

        return fieldCol;
    }

    private void completeFieldDetailsIfNecessary(Entry<String, Object> entry, Map<String, Object> fldDetails) {
        if (!fldDetails.containsKey("name")) {
            fldDetails.put("name", entry.getKey());
        }
        if (!fldDetails.containsKey("description")) {
            fldDetails.put("description", entry.getKey());
        }

        if (!fldDetails.containsKey("type")) {
            Class<?> type = entry.getValue().getClass();
            if (type == String.class) {
                fldDetails.put("type", "char");
            } else if (type == Date.class) {
                fldDetails.put("type", "date");
            } else if (type == Boolean.class) {
                fldDetails.put("type", "boolean");
            } else if (type == Double.class) {
                fldDetails.put("type", "float");
            } else if (type == Integer.class) {
                fldDetails.put("type", "integer");
            } else {
                fldDetails.put("type", "char");
            }
        }
    }

    /**
     * Calls any function on an object. The first row is inspected to determine data fields and data types The Odoo
     * function must have the signature like (self, cr, uid, *param) and return a dictionary or object.
     *
     * @param functionName function to call
     * @param parameters Additional parameters that will be passed to the object
     * @param fieldCol An option field collection to use. A new one will be built by inspecting the first row if it
     * isn't specified (null).
     * @return A row collection with the data
     * @throws OdooApiException
     */
    public RowCollection callFunction(String functionName, Object[] parameters, FieldCollection fieldCol)
            throws OdooApiException {
        Response response = command.callObjectFunction(modelName, functionName, parameters);

        if (!response.isSuccessful()) {
            String message = "Failed to call function '" + functionName + "' with parameters '"
                    + Arrays.deepToString(parameters) + "' and FieldCollection '" + fieldCol + "' on object '"
                    + modelName + "'";
            throw new OdooApiException(message, response.getErrorCause());
        }

        Object[] results = response.getResponseObjectAsArray();

        FieldCollection fieldCollection = fieldCol != null ? fieldCol : callFieldsFunction(response);

        return new RowCollection(results, fieldCollection);
    }

    /**
     * Executes a workflow by sending a signal to the workflow engine for a specific object.
     *
     * @param mapRow MapRow that represents the object that the signal should be sent for
     * @param signal Signal name to send
     * @throws OdooApiException
     */
    public void executeWorkflow(MapRow mapRow, String signal) throws OdooApiException {
        try {
            this.checkSignalExists(signal);
            this.command.executeWorkflow(this.modelName, signal, mapRow.getID());
        }
        catch (XmlRpcException ex) {
            throw new XmlRpcRuntimeException(ex);
        }
    }

    /**
     * Deletes objects from the Odoo Server
     *
     * @param rows Rows to delete
     * @return If all rows were successfully deleted
     */
    public boolean unlinkObject(RowCollection rows) {
        try {
            //Object[] ids = new Object[rows.size()];
            //for (int i = 0; i < rows.size(); i++) ids[i] = rows.get(i).getID();
            //return this.command.unlinkObject(this.modelName, ids);
            return this.command.unlinkObject(this.modelName,
                    rows.stream().map(row -> row.getID()).toArray());
        } catch (XmlRpcException ex) {
            throw new XmlRpcRuntimeException(ex);
        }
    }

    /**
     * Deletes objects from the Odoo Server
     *
     * @param mapRow MapRow to delete
     * @return If the row was successfully deleted
     */
    public boolean unlinkObject(final MapRow mapRow) {
        final RowCollection rows = new RowCollection();
        rows.add(mapRow);
        return this.unlinkObject(rows);
    }

//    private Object[] fixImportData(Row inputRow) throws OdooApiException {
//        try {
//            // +1 because we need to include the ID field
//            Object[] outputRow = new Object[inputRow.getFields().size() + 1];
//
//            // ID must be an integer
//            outputRow[0] = inputRow.get("id");
//            if (outputRow[0] == null) {
//                outputRow[0] = 0;
//            } else {
//                outputRow[0] = Integer.parseInt(inputRow.get("id").toString());
//            }
//
//            for (int i = 0; i < inputRow.getFields().size(); i++) {
//                int columnIndex = i + 1;
//
//                Field fld = inputRow.getFields().get(i);
//                String fieldName = fld.getName();
//                Object value = inputRow.get(fieldName);
//
//                outputRow[columnIndex] = value;
//
//                if (fld.getType() == FieldType.MANY2ONE) {
//                    if (value == null) {
//                        outputRow[columnIndex] = 0;
//                    } else {
//                        outputRow[columnIndex] = Integer.parseInt(value.toString());
//                    }
//                    continue;
//                }
//
//                // Null values must be false
//                if (value == null) {
//                    outputRow[columnIndex] = false;
//                    continue;
//                }
//
//                value = formatValueForWrite(fld, value);
//
//                // Check types
//                switch (fld.getType()) {
//                    case SELECTION:
//                        boolean validValue = false;
//                        for (SelectionOption option : fld.getSelectionOptions()) {
//                            // If the database code was specified, replace it with the
//                            // value.
//                            // The import procedure uses the value and not the code
//                            if (option.code.equals(value.toString())) {
//                                validValue = true;
//                                outputRow[columnIndex] = option.value;
//                                break;
//                            } else if (option.value.equals(value.toString())) {
//                                outputRow[columnIndex] = value;
//                                validValue = true;
//                                break;
//                            }
//                        }
//                        if (!validValue) {
//                            throw new OdooApiException(
//                                    "Could not find a valid value for section field " + fieldName + " with value " + value);
//                        }
//                        break;
//                    case MANY2MANY:
//                        /*
//				 * The import function uses the Names of the objects for the
//				 * import. Replace the ID list passed in with a Name list for
//				 * the import_data function that we are about to call
//                         */
//                        Map<String, String> idToName;
//                        if (!modelNameCache.containsKey(fld.getRelation())) {
//                            idToName = new HashMap<>();
//                            //Object[] ids = new Object[]{};
//                            Response response = command.searchObject(fld.getRelation(), new Object[]{});
//                            if (response.isSuccessful()) {
//                                Object[] ids = response.getResponseObjectAsArray();
//                                Object[] names = command.nameGet(fld.getRelation(), ids);
//                                for (int j = 0; j < ids.length; j++) {
//                                    Object[] nameValue = (Object[]) names[j];
//                                    idToName.put(nameValue[0].toString(), nameValue[1].toString());
//                                }
//                            }
//
//                            modelNameCache.put(fld.getRelation(), idToName);
//                        } else {
//                            idToName = modelNameCache.get(fld.getRelation());
//                        }
//
//                        String newValue = "";
//                        // Comma separated list of IDs
//                        if (value instanceof String) {
//                            for (String singleID : value.toString().split(",")) {
//                                if (idToName.containsKey(singleID)) {
//                                    newValue = newValue + "," + idToName.get(singleID);
//                                } else {
//                                    throw new OdooApiException(
//                                            "Could not find " + fld.getRelation() + " with ID " + singleID);
//                                }
//                            }
//                        } else {
//                            // Object[] of values -- default
//                            for (Object singleID : (Object[]) value) {
//                                if (idToName.containsKey(singleID.toString())) {
//                                    newValue = newValue + "," + idToName.get(singleID.toString());
//                                } else {
//                                    throw new OdooApiException(
//                                            "Could not find " + fld.getRelation() + " with ID " + singleID.toString());
//                                }
//                            }
//                        }
//                        outputRow[columnIndex] = newValue.substring(1);
//
//                        break;
//
//                    // The import procedure expects most types to be strings
//                    default:
//                        outputRow[columnIndex] = value.toString();
//                        break;
//                }
//            }
//
//            return outputRow;
//        } catch (XmlRpcException ex) {
//            throw new XmlRpcRuntimeException(ex);
//        }
//    }
}
