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
import java.util.HashMap;
import lombok.NoArgsConstructor;

/**
 * *
 * Row collection for OpenERP row data
 *
 * @author Pieter van der Merwe
 *
 */
@NoArgsConstructor
public class RowCollection extends ArrayList<MapRow> {

    private static final long serialVersionUID = -168965138153400087L;

    public RowCollection(final Object[] openERPResultSet, final FieldCollection fields) throws OdooApiException {
        for (final Object openERPResult : openERPResultSet) {
            super.add(new MapRow((HashMap<String, Object>) openERPResult, fields));
        }
    }

    @Override
    public void add(final int index, final MapRow element) {
        super.add(index, element);
    }

    @Override
    public boolean add(final MapRow mapRow) {
        return super.add(mapRow);
    }

}
