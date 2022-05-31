package com.odoojava.api;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.Serializable;
import java.util.List;

public class MapRow implements Serializable {

    private static final long serialVersionUID = 8593471239365171312L;
    
    private final HashMap<String, Object> openERPResult;
    private final FieldCollection fields;
    private final List<RowChangedListener> rowChangedListeners;
    private final FieldCollection changedFields;

    public MapRow(final HashMap<String, Object> openERPResult, final FieldCollection fields) throws OdooApiException {
        this.changedFields = new FieldCollection();
        this.rowChangedListeners = new ArrayList<>();
        this.openERPResult = openERPResult;
        this.fields = fields;
        if (openERPResult.isEmpty()) {
            this.put("id", 0);
            for (int i = 0; i < fields.size(); ++i) {
                this.put(fields.get(i).getName(), null);
            }
        }
    }

    public int getID() {
        return Integer.parseInt(String.valueOf(this.get("id")));
    }

    public void addRowChangedLisener(final RowChangedListener listener) {
        if (!rowChangedListeners.contains(listener)) rowChangedListeners.add(listener);
    }

    public MapRow(final MapRow templateMapRow) {
        this.changedFields = new FieldCollection();
        this.rowChangedListeners = new ArrayList<>();
        this.openERPResult = (HashMap<String, Object>) templateMapRow.openERPResult.clone();
        this.fields = (FieldCollection) templateMapRow.fields.clone();
    }

    public FieldCollection getFields() {
        return fields;
    }

    public Object get(final Field field) {
        return get(field.getName());
    }

    public Object get(final String fieldName) {
        if (fieldName != null && fieldName.equals("id")) {
            return this.openERPResult.get(fieldName);
        }
        final Field fieldMeta = this.getField(fieldName);
        if (fieldMeta == null) {
            return null;
        }
        final Object value = this.openERPResult.get(fieldName);
        final Field.FieldType fieldType = fieldMeta.getType();
        if (fieldType != Field.FieldType.BOOLEAN && value instanceof Boolean) {
            return null;
        }
        if (value instanceof Object[] && ((Object[]) value).length == 0) {
            return null;
        }
        if (value instanceof String && fieldType == Field.FieldType.DATE) {
            return DateTimeFormatter.parseDate(value);
        }
        if (value instanceof String && fieldType == Field.FieldType.DATETIME) {
            return DateTimeFormatter.parseDateTime(value);
        }
        return value;
    }

    private Field getField(final String fieldName) {
        return this.fields.stream()
                .filter(fld -> fld.getName().equals(fieldName))
                .findAny()
                .orElse(null);
    }

    public final void put(final String fieldName, Object value) throws OdooApiException {
        if (fieldName.equals("id")) {
            openERPResult.put(fieldName, value);
            return;
        }
        final Field fld = this.getField(fieldName);
        if (fld == null) throw new OdooApiException("Field '" + fieldName + "' was not found in row");
        final Field.FieldType fieldType = fld.getType();
        if (fieldType == Field.FieldType.ONE2MANY) value = new Object[]{value, null};
        if (this.openERPResult.containsKey(fieldName)) {
            final Object oldValue = openERPResult.get(fieldName);
            if (oldValue == null && value == null) return;
            if ((oldValue != null || value == null) && (oldValue == null || value != null) && value.equals(oldValue)) {
                return;
            }
            openERPResult.remove(fieldName);
        }
        this.openERPResult.put(fieldName, value);
        this.getChangedFields().add(fld);
        for (final RowChangedListener listener : this.rowChangedListeners) {
            listener.rowChanged(fld, this);
        }
    }

    public void putMany2ManyValue(final String fieldName, final Object[] values, final boolean append) throws OdooApiException {
        final Field fld = this.getField(fieldName);
        if (fld.getType() != Field.FieldType.MANY2MANY)
            throw new OdooApiException("Field '" + fieldName + "' is not a many2many field");
        final Object currentValue = get(fieldName);
        if (currentValue == null) put(fieldName, values);
        final List newValues = new ArrayList<>();
        if (append) Collections.addAll(newValues, (Object[]) currentValue);
        for (final Object val : values) {
            if (!newValues.contains(val)) newValues.add(val);
        }
        this.put(fieldName, newValues.toArray(new Object[newValues.size()]));
    }

    public void changesApplied() {
        this.changedFields.clear();
    }

    public FieldCollection getChangedFields() {
        return this.changedFields;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("MapRow(openERPResult=").append(openERPResult)
                .append(", fields=").append(getFields())
                .append(", rowChangedListeners=").append(rowChangedListeners)
                .append(", changedFields=").append(getChangedFields()).append(")")
                .toString();
    }

    public interface RowChangedListener extends Serializable {
        void rowChanged(Field field, MapRow mapRow);
    }

}
